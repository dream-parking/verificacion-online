package com.dreamparking.backend.onboarding.service;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.catalog.entity.IncomeSource;
import com.dreamparking.backend.catalog.entity.MonthlyAmountRange;
import com.dreamparking.backend.catalog.service.CatalogService;
import com.dreamparking.backend.catalog.service.PrivacyNoticeService;
import com.dreamparking.backend.common.exception.InvalidInputException;
import com.dreamparking.backend.common.exception.InvalidStateException;
import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.customer.entity.Customer;
import com.dreamparking.backend.customer.service.CustomerService;
import com.dreamparking.backend.identity.entity.enums.DuiDateProblem;
import com.dreamparking.backend.identity.entity.enums.OcrStatus;
import com.dreamparking.backend.identity.repository.IdentityDocumentRepository;
import com.dreamparking.backend.liveness.entity.LivenessStatus;
import com.dreamparking.backend.onboarding.dto.BasicDataRequest;
import com.dreamparking.backend.onboarding.dto.ExpectedActivityRequest;
import com.dreamparking.backend.onboarding.dto.IncomeDeclarationRequest;
import com.dreamparking.backend.onboarding.dto.OnboardingRequestResponse;
import com.dreamparking.backend.onboarding.entity.ExpectedActivity;
import com.dreamparking.backend.onboarding.entity.ExpectedActivityVersion;
import com.dreamparking.backend.onboarding.entity.IncomeDeclaration;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.onboarding.entity.PrivacyConsent;
import com.dreamparking.backend.onboarding.entity.RequestEvent;
import com.dreamparking.backend.onboarding.entity.enums.OnboardingStep;
import com.dreamparking.backend.onboarding.entity.enums.ProfileChangedBy;
import com.dreamparking.backend.onboarding.entity.enums.RequestEventType;
import com.dreamparking.backend.onboarding.entity.enums.RequestStatus;
import com.dreamparking.backend.onboarding.repository.ExpectedActivityRepository;
import com.dreamparking.backend.onboarding.repository.ExpectedActivityVersionRepository;
import com.dreamparking.backend.onboarding.repository.IncomeDeclarationRepository;
import com.dreamparking.backend.onboarding.repository.OnboardingRequestRepository;
import com.dreamparking.backend.onboarding.repository.PrivacyConsentRepository;
import com.dreamparking.backend.onboarding.repository.RequestEventRepository;
import com.dreamparking.backend.risk.dto.RiskAssessmentResponse;
import com.dreamparking.backend.risk.service.RiskAssessmentService;
import com.dreamparking.backend.risk.service.RiskDecisionService;
import com.dreamparking.backend.rnpn.client.RnpnQuery;
import com.dreamparking.backend.rnpn.entity.RnpnCheck;
import com.dreamparking.backend.rnpn.entity.enums.RnpnOutcome;
import com.dreamparking.backend.rnpn.repository.RnpnCheckRepository;
import com.dreamparking.backend.rnpn.service.RnpnService;

/**
 * Steps of the mobile onboarding flow: privacy notice → basic data → identity document (DUI) → income → expected
 * activity → submit. Steps can be saved again while the request is in progress; {@code completedSteps} only advances
 * in order, and the request can only be submitted once the five steps are done. The DUI step is captured and
 * confirmed through {@code IdentityDocumentService}. It is required; only tests or local runs make it optional
 * ({@code app.onboarding.identity-document-required=false}), and then the income step skips it. The time spent on each step is measured by
 * the app and stored by {@link SignalsService}: this service only tracks the progress.
 */
@Service
public class OnboardingService {

	private static final String OTHER_INCOME_SOURCE = "OTRO";

	private final OnboardingRequestRepository requests;

	private final PrivacyConsentRepository consents;

	private final IncomeDeclarationRepository incomeDeclarations;

	private final ExpectedActivityRepository expectedActivities;

	private final RequestEventRepository events;

	private final CatalogService catalogService;

	private final PrivacyNoticeService privacyNoticeService;

	private final CustomerService customerService;

	private final RiskAssessmentService riskAssessmentService;

	private final RiskDecisionService riskDecisionService;

	private final RnpnCheckRepository rnpnChecks;

	private final IdentityDocumentRepository identityDocuments;

	private final ExpectedActivityVersionRepository profileVersions;

	private final boolean identityDocumentRequired;

	public OnboardingService(OnboardingRequestRepository requests, PrivacyConsentRepository consents,
			IncomeDeclarationRepository incomeDeclarations, ExpectedActivityRepository expectedActivities,
			RequestEventRepository events, CatalogService catalogService,
			PrivacyNoticeService privacyNoticeService, CustomerService customerService,
			RiskAssessmentService riskAssessmentService, RiskDecisionService riskDecisionService,
			RnpnCheckRepository rnpnChecks, IdentityDocumentRepository identityDocuments,
			ExpectedActivityVersionRepository profileVersions,
			@Value("${app.onboarding.identity-document-required:true}") boolean identityDocumentRequired) {
		this.requests = requests;
		this.consents = consents;
		this.incomeDeclarations = incomeDeclarations;
		this.expectedActivities = expectedActivities;
		this.events = events;
		this.catalogService = catalogService;
		this.privacyNoticeService = privacyNoticeService;
		this.customerService = customerService;
		this.riskAssessmentService = riskAssessmentService;
		this.riskDecisionService = riskDecisionService;
		this.rnpnChecks = rnpnChecks;
		this.identityDocuments = identityDocuments;
		this.profileVersions = profileVersions;
		this.identityDocumentRequired = identityDocumentRequired;
	}

	@Transactional
	public OnboardingRequestResponse start() {
		OnboardingRequest request = requests.save(new OnboardingRequest());
		recordEvent(request, RequestEventType.REQUEST_STARTED, "Solicitud iniciada");
		return OnboardingRequestResponse.of(request);
	}

	@Transactional(readOnly = true)
	public OnboardingRequestResponse get(UUID requestId) {
		return OnboardingRequestResponse.of(find(requestId));
	}

	/**
	 * Step 1: accepts the privacy notice in force, recording the notice version, the date and time, where it was
	 * accepted from and whether the terms and conditions were accepted ({@code null} when the app did not send it).
	 */
	@Transactional
	public OnboardingRequestResponse acceptPrivacyNotice(UUID requestId, Boolean termsAccepted, InetAddress ip) {
		OnboardingRequest request = findInProgress(requestId);

		PrivacyConsent consent = consents.findById(requestId).orElseGet(PrivacyConsent::new);
		consent.setRequest(request);
		consent.setNotice(privacyNoticeService.current());
		consent.setSignalsAccepted(true);
		consent.setTermsAccepted(termsAccepted);
		consent.setIp(ip);
		consents.save(consent);

		completeStep(request, OnboardingStep.PRIVACY_NOTICE);
		recordEvent(request, RequestEventType.PRIVACY_ACCEPTED, "Aviso de privacidad aceptado");
		return OnboardingRequestResponse.of(request);
	}

	/** Step 2: identifies the customer. The request keeps a snapshot of the data as declared. */
	@Transactional
	public OnboardingRequestResponse registerBasicData(UUID requestId, BasicDataRequest body) {
		OnboardingRequest request = findInProgress(requestId);

		Customer customer = customerService.register(body.dui(), body.firstNames().trim(), body.lastNames().trim(),
				body.mobilePhone(), request.getCustomer());
		request.setCustomer(customer);
		request.setDui(customer.getDui());
		request.setFirstNames(customer.getFirstNames());
		request.setLastNames(customer.getLastNames());
		request.setMobilePhone(customer.getMobilePhone());
		// Corrected after confirming the DUI: compare again with what the customer confirmed.
		identityDocuments.findById(requestId)
			.ifPresent(document -> document.compareWithDeclared(request.getDui(), request.getFirstNames(),
					request.getLastNames()));

		completeStep(request, OnboardingStep.BASIC_DATA);
		recordEvent(request, RequestEventType.BASIC_DATA_COMPLETED, "Datos básicos completados");
		return OnboardingRequestResponse.of(request);
	}

	/** The DUI photos of the request were stored and read (or could not be read); the step does not advance yet. */
	@Transactional
	public void recordIdentityDocumentCaptured(OnboardingRequest request, OcrStatus ocrStatus) {
		String description = switch (ocrStatus) {
			case READ -> "Fotos del DUI capturadas y leídas";
			case UNREADABLE -> "Fotos del DUI ilegibles";
			case FAILED -> "Fotos del DUI capturadas; el lector no estuvo disponible";
		};
		recordEvent(request, RequestEventType.IDENTITY_DOCUMENT_CAPTURED, description,
				Map.of("ocrStatus", ocrStatus.name()));
	}

	/** The selfie of the request went through the liveness check (VDI-114); the confidence stays in the file, not in the timeline. */
	@Transactional
	public void recordLivenessChecked(OnboardingRequest request, LivenessStatus status) {
		String description = status == LivenessStatus.CHECKED ? "Prueba de vida realizada"
				: "Prueba de vida capturada; el proveedor no estuvo disponible";
		recordEvent(request, RequestEventType.LIVENESS_CHECKED, description, Map.of("livenessStatus", status.name()));
	}

	/** The customer tried to confirm an expired DUI or dates that do not agree; the step does not advance. */
	@Transactional
	public void recordIdentityDocumentRejected(OnboardingRequest request, List<DuiDateProblem> problems) {
		recordEvent(request, RequestEventType.IDENTITY_DOCUMENT_REJECTED,
				"Confirmación del DUI rechazada: " + String.join("; ", problems.stream().map(DuiDateProblem::label).toList()),
				Map.of("reasons", problems.stream().map(Enum::name).toList()));
	}

	/** Step 3: the customer confirmed or corrected the data read from the DUI. */
	@Transactional
	public void completeIdentityDocument(OnboardingRequest request) {
		completeStep(request, OnboardingStep.IDENTITY_DOCUMENT);
		recordEvent(request, RequestEventType.IDENTITY_DOCUMENT_CONFIRMED, "Datos del DUI confirmados");
	}

	/**
	 * Step 4: saves (or replaces) the declared income source and range. While the DUI step is optional, a request
	 * right after the basic data skips it.
	 */
	@Transactional
	public void declareIncome(UUID requestId, IncomeDeclarationRequest body) {
		OnboardingRequest request = findInProgress(requestId);
		if (!identityDocumentRequired && request.getCompletedSteps() == OnboardingStep.IDENTITY_DOCUMENT.ordinal()) {
			request.setCompletedSteps((short) (OnboardingStep.IDENTITY_DOCUMENT.ordinal() + 1));
		}
		IncomeSource source = catalogService.activeIncomeSource(body.sourceCode());
		if (OTHER_INCOME_SOURCE.equals(source.getCode())
				&& (body.sourceDetail() == null || body.sourceDetail().isBlank())) {
			throw new InvalidInputException("sourceDetail is required when the income source is " + OTHER_INCOME_SOURCE);
		}

		IncomeDeclaration declaration = incomeDeclarations.findById(requestId).orElseGet(IncomeDeclaration::new);
		declaration.setRequest(request);
		declaration.setSource(source);
		declaration.setSourceDetail(body.sourceDetail());
		declaration.setRange(catalogService.activeIncomeRange(body.rangeCode()));
		incomeDeclarations.save(declaration);

		completeStep(request, OnboardingStep.INCOME);
		recordEvent(request, RequestEventType.INCOME_REGISTERED, "Ingresos registrados");
	}

	/**
	 * Step 5: saves (or replaces) the expected activity and scores the request's risk (see
	 * {@link RiskAssessmentService}). Besides the main type and the range, the customer may declare other types of
	 * money, an estimated amount inside the range, how many incoming movements and how much outflow a month: the
	 * baseline of VDI-3.
	 */
	@Transactional
	public RiskAssessmentResponse registerExpectedActivity(UUID requestId, ExpectedActivityRequest body) {
		OnboardingRequest request = findInProgress(requestId);

		ExpectedActivity activity = expectedActivities.findById(requestId).orElseGet(ExpectedActivity::new);
		activity.setRequest(request);
		activity.setTransactionType(catalogService.activeTransactionType(body.transactionTypeCode()));
		activity.setExtraTransactionTypeCodes(extraTransactionTypes(body));
		MonthlyAmountRange range = catalogService.activeMonthlyAmountRange(body.monthlyAmountRangeCode());
		activity.setMonthlyAmountRange(range);
		activity.setMonthlyAmountUsd(amountInsideRange(body.monthlyAmountUsd(), range));
		activity.setMonthlyTransactionCount(
				body.monthlyTransactionCount() == null ? null : body.monthlyTransactionCount().shortValue());
		activity.setMonthlyOutflowUsd(body.monthlyOutflowUsd());
		expectedActivities.save(activity);
		short version = recordProfileVersion(activity);

		completeStep(request, OnboardingStep.EXPECTED_ACTIVITY);
		recordEvent(request, RequestEventType.EXPECTED_ACTIVITY_REGISTERED,
				"Movimiento esperado registrado (versión %d)".formatted(version), Map.of("version", version));
		return riskAssessmentService.evaluate(request, range);
	}

	/**
	 * VDI-3: a declaration that changes something is a new version of the declared profile; the previous ones stay as
	 * they were. Sending the same again adds nothing. Returns the version in force.
	 */
	private short recordProfileVersion(ExpectedActivity activity) {
		Optional<ExpectedActivityVersion> latest = profileVersions
			.findFirstByRequest_IdOrderByVersionDesc(activity.getRequest().getId());
		if (latest.isPresent() && latest.get().declaresTheSameAs(activity)) {
			return latest.get().getVersion();
		}
		short next = (short) (latest.map(ExpectedActivityVersion::getVersion).orElse((short) 0) + 1);
		profileVersions.save(new ExpectedActivityVersion(activity, next, ProfileChangedBy.CUSTOMER));
		return next;
	}

	/** Extra types of money: active catalog codes, without repeating the main type or each other. */
	private Set<String> extraTransactionTypes(ExpectedActivityRequest body) {
		Set<String> codes = new LinkedHashSet<>();
		if (body.additionalTransactionTypes() == null) {
			return codes;
		}
		for (ExpectedActivityRequest.AdditionalTransactionType type : body.additionalTransactionTypes()) {
			String active = catalogService.activeTransactionType(type.code()).getCode();
			if (active.equals(body.transactionTypeCode()) || !codes.add(active)) {
				throw new InvalidInputException("additionalTransactionTypes repeats the type " + active);
			}
		}
		return codes;
	}

	/** The estimated monthly amount has to agree with the chosen range (a null bound is open-ended). */
	private static BigDecimal amountInsideRange(BigDecimal amount, MonthlyAmountRange range) {
		if (amount == null) {
			return null;
		}
		boolean belowMin = range.getMinUsd() != null && amount.compareTo(range.getMinUsd()) < 0;
		boolean aboveMax = range.getMaxUsd() != null && amount.compareTo(range.getMaxUsd()) > 0;
		if (belowMin || aboveMax) {
			throw new InvalidInputException("monthlyAmountUsd " + amount.toPlainString()
					+ " is outside the monthly amount range " + range.getCode());
		}
		return amount;
	}

	/**
	 * Checks that the request can be submitted and returns the confirmed DUI data the RNPN is asked about (VDI-111);
	 * empty when there is no confirmed DUI to check. It changes nothing, so the RNPN can be asked before the
	 * transaction of {@link #submit(UUID, RnpnService.Answer)} starts.
	 */
	@Transactional(readOnly = true)
	public Optional<RnpnQuery> prepareSubmission(UUID requestId) {
		OnboardingRequest request = findInProgress(requestId);
		validateSubmittable(request);
		return identityDocuments.findById(requestId)
			.map(document -> new RnpnQuery(document.getConfirmedDui(), document.getConfirmedFirstNames(),
					document.getConfirmedLastNames(), document.getConfirmedBirthDate()));
	}

	/** Review and submit without checking the RNPN: assigns the SOL-YYYY-NNNNN number and scores the request. */
	@Transactional
	public OnboardingRequestResponse submit(UUID requestId) {
		return submit(requestId, null);
	}

	/**
	 * Review and submit: assigns the SOL-YYYY-NNNNN number, closes the request and scores it with every signal.
	 * {@code rnpnAnswer} is what the RNPN answered about the confirmed DUI, stored in the file before scoring so it counts
	 * in the score; when the RNPN did not answer the request waits and is not approved automatically.
	 */
	@Transactional
	public OnboardingRequestResponse submit(UUID requestId, RnpnService.Answer rnpnAnswer) {
		OnboardingRequest request = findInProgress(requestId);
		validateSubmittable(request);

		Instant now = Instant.now();
		completeStep(request, OnboardingStep.REVIEW);
		request.setNumber(requests.nextNumber(now));
		request.setStatus(RequestStatus.COMPLETED);
		request.setSubmittedAt(now);
		recordEvent(request, RequestEventType.REQUEST_SUBMITTED, "Solicitud enviada",
				Map.of("number", request.getNumber()));
		// The customer gets their permanent number with the first request they submit; a customer that has one keeps it.
		if (request.getCustomer() != null) {
			customerService.assignNumber(request.getCustomer().getId());
		}
		if (rnpnAnswer != null) {
			recordRnpnCheck(request, rnpnAnswer);
		}
		// The signals captured after the amount was declared (telemetry, DUI) count in the final score.
		expectedActivities.findById(requestId)
			.ifPresent(activity -> riskAssessmentService.evaluate(request, activity.getMonthlyAmountRange()));
		// Above the threshold of the score model the request is not approved automatically (VDI-117).
		riskDecisionService.decideOnSubmission(request);
		return OnboardingRequestResponse.of(request);
	}

	private void validateSubmittable(OnboardingRequest request) {
		int stepsBeforeReview = OnboardingStep.REVIEW.ordinal();
		if (request.getCompletedSteps() < stepsBeforeReview) {
			throw new InvalidStateException("Complete the " + stepsBeforeReview + " previous steps before submitting");
		}
		if (identityDocuments.findById(request.getId()).filter(document -> !document.isConfirmed()).isPresent()) {
			// New DUI photos after confirming: the customer has to confirm their data again.
			throw new InvalidStateException("Confirm the data of the identity document before submitting");
		}
	}

	/** Stores the RNPN check in the file and the timeline; an RNPN that did not answer leaves the request waiting. */
	private void recordRnpnCheck(OnboardingRequest request, RnpnService.Answer answer) {
		rnpnChecks.save(new RnpnCheck(request, answer.outcome(), answer.provider(), answer.attempts(),
				answer.mismatchedFields(), answer.failure()));
		String description = switch (answer.outcome()) {
			case MATCH -> "RNPN check done: the data matches";
			case NO_MATCH -> "RNPN check done: the data does not match";
			case UNAVAILABLE -> "RNPN check not done: the RNPN did not answer";
		};
		recordEvent(request, RequestEventType.RNPN_CHECKED, description,
				Map.of("outcome", answer.outcome().name(), "attempts", answer.attempts(), "provider", answer.provider()));
		if (answer.outcome() == RnpnOutcome.UNAVAILABLE) {
			request.setStatus(RequestStatus.AWAITING_RNPN);
		}
	}

	/** Request entity for other onboarding services. */
	@Transactional(readOnly = true)
	public OnboardingRequest findInProgress(UUID requestId) {
		OnboardingRequest request = find(requestId);
		if (request.getStatus() != RequestStatus.IN_PROGRESS) {
			throw new InvalidStateException("Onboarding request " + requestId + " is " + request.getStatus());
		}
		return request;
	}

	private OnboardingRequest find(UUID requestId) {
		return requests.findById(requestId)
			.orElseThrow(() -> new NotFoundException("Onboarding request not found: " + requestId));
	}

	/**
	 * Advances {@code completedSteps} when this is the next step in order. Step times and attempts are not written
	 * here: the app is their only source (it measures from when the customer sees the screen) and sends them to
	 * {@code PUT /signals}.
	 */
	private void completeStep(OnboardingRequest request, OnboardingStep step) {
		Instant now = Instant.now();
		int stepNumber = step.ordinal() + 1;
		if (request.getCompletedSteps() == stepNumber - 1) {
			request.setCompletedSteps((short) stepNumber);
		}
		request.setLastActivityAt(now);
	}

	private void recordEvent(OnboardingRequest request, RequestEventType type, String description) {
		recordEvent(request, type, description, Map.of());
	}

	private void recordEvent(OnboardingRequest request, RequestEventType type, String description,
			Map<String, Object> data) {
		RequestEvent event = new RequestEvent();
		event.setRequest(request);
		event.setType(type);
		event.setDescription(description);
		event.setData(data);
		events.save(event);
	}

}
