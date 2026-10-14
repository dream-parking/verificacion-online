package com.dreamparking.backend.onboarding.service;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import com.dreamparking.backend.onboarding.dto.BasicDataRequest;
import com.dreamparking.backend.onboarding.dto.ExpectedActivityRequest;
import com.dreamparking.backend.onboarding.dto.IncomeDeclarationRequest;
import com.dreamparking.backend.onboarding.dto.OnboardingRequestResponse;
import com.dreamparking.backend.onboarding.entity.ExpectedActivity;
import com.dreamparking.backend.onboarding.entity.IncomeDeclaration;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.onboarding.entity.PrivacyConsent;
import com.dreamparking.backend.onboarding.entity.RequestEvent;
import com.dreamparking.backend.onboarding.entity.enums.OnboardingStep;
import com.dreamparking.backend.onboarding.entity.enums.RequestEventType;
import com.dreamparking.backend.onboarding.entity.enums.RequestStatus;
import com.dreamparking.backend.onboarding.repository.ExpectedActivityRepository;
import com.dreamparking.backend.onboarding.repository.IncomeDeclarationRepository;
import com.dreamparking.backend.onboarding.repository.OnboardingRequestRepository;
import com.dreamparking.backend.onboarding.repository.PrivacyConsentRepository;
import com.dreamparking.backend.onboarding.repository.RequestEventRepository;
import com.dreamparking.backend.risk.dto.RiskAssessmentResponse;
import com.dreamparking.backend.risk.service.RiskAssessmentService;

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

	private final IdentityDocumentRepository identityDocuments;

	private final boolean identityDocumentRequired;

	public OnboardingService(OnboardingRequestRepository requests, PrivacyConsentRepository consents,
			IncomeDeclarationRepository incomeDeclarations, ExpectedActivityRepository expectedActivities,
			RequestEventRepository events, CatalogService catalogService,
			PrivacyNoticeService privacyNoticeService, CustomerService customerService,
			RiskAssessmentService riskAssessmentService, IdentityDocumentRepository identityDocuments,
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
		this.identityDocuments = identityDocuments;
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

	/** Step 1: accepts the privacy notice in force, recording where it was accepted from. */
	@Transactional
	public OnboardingRequestResponse acceptPrivacyNotice(UUID requestId, InetAddress ip) {
		OnboardingRequest request = findInProgress(requestId);

		PrivacyConsent consent = consents.findById(requestId).orElseGet(PrivacyConsent::new);
		consent.setRequest(request);
		consent.setNotice(privacyNoticeService.current());
		consent.setSignalsAccepted(true);
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

		completeStep(request, OnboardingStep.EXPECTED_ACTIVITY);
		recordEvent(request, RequestEventType.EXPECTED_ACTIVITY_REGISTERED, "Movimiento esperado registrado");
		return riskAssessmentService.evaluate(request, range);
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

	/** Review and submit: assigns the SOL-YYYY-NNNNN number, closes the request and scores it with every signal. */
	@Transactional
	public OnboardingRequestResponse submit(UUID requestId) {
		OnboardingRequest request = findInProgress(requestId);
		int stepsBeforeReview = OnboardingStep.REVIEW.ordinal();
		if (request.getCompletedSteps() < stepsBeforeReview) {
			throw new InvalidStateException("Complete the " + stepsBeforeReview + " previous steps before submitting");
		}
		if (identityDocuments.findById(requestId).filter(document -> !document.isConfirmed()).isPresent()) {
			// New DUI photos after confirming: the customer has to confirm their data again.
			throw new InvalidStateException("Confirm the data of the identity document before submitting");
		}

		Instant now = Instant.now();
		completeStep(request, OnboardingStep.REVIEW);
		request.setNumber(requests.nextNumber(now));
		request.setStatus(RequestStatus.COMPLETED);
		request.setSubmittedAt(now);
		recordEvent(request, RequestEventType.REQUEST_SUBMITTED, "Solicitud enviada",
				Map.of("number", request.getNumber()));
		// The signals captured after the amount was declared (telemetry, DUI) count in the final score.
		expectedActivities.findById(requestId)
			.ifPresent(activity -> riskAssessmentService.evaluate(request, activity.getMonthlyAmountRange()));
		return OnboardingRequestResponse.of(request);
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
