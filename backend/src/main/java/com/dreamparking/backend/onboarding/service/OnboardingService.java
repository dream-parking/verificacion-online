package com.dreamparking.backend.onboarding.service;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

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
 * Steps of the mobile onboarding flow: privacy notice → basic data → income → expected activity → submit.
 * Steps can be saved again while the request is in progress; {@code completedSteps} only advances in order,
 * and the request can only be submitted once the four steps are done. The time spent on each step is measured by
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

	public OnboardingService(OnboardingRequestRepository requests, PrivacyConsentRepository consents,
			IncomeDeclarationRepository incomeDeclarations, ExpectedActivityRepository expectedActivities,
			RequestEventRepository events, CatalogService catalogService,
			PrivacyNoticeService privacyNoticeService, CustomerService customerService,
			RiskAssessmentService riskAssessmentService) {
		this.requests = requests;
		this.consents = consents;
		this.incomeDeclarations = incomeDeclarations;
		this.expectedActivities = expectedActivities;
		this.events = events;
		this.catalogService = catalogService;
		this.privacyNoticeService = privacyNoticeService;
		this.customerService = customerService;
		this.riskAssessmentService = riskAssessmentService;
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
				body.mobilePhone());
		request.setCustomer(customer);
		request.setDui(customer.getDui());
		request.setFirstNames(customer.getFirstNames());
		request.setLastNames(customer.getLastNames());
		request.setMobilePhone(customer.getMobilePhone());

		completeStep(request, OnboardingStep.BASIC_DATA);
		recordEvent(request, RequestEventType.BASIC_DATA_COMPLETED, "Datos básicos completados");
		return OnboardingRequestResponse.of(request);
	}

	/** Step 3: saves (or replaces) the declared income source and range. */
	@Transactional
	public void declareIncome(UUID requestId, IncomeDeclarationRequest body) {
		OnboardingRequest request = findInProgress(requestId);
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

	/** Step 4: saves (or replaces) the expected activity and scores the request's risk from the monthly amount range. */
	@Transactional
	public RiskAssessmentResponse registerExpectedActivity(UUID requestId, ExpectedActivityRequest body) {
		OnboardingRequest request = findInProgress(requestId);

		ExpectedActivity activity = expectedActivities.findById(requestId).orElseGet(ExpectedActivity::new);
		activity.setRequest(request);
		activity.setTransactionType(catalogService.activeTransactionType(body.transactionTypeCode()));
		MonthlyAmountRange range = catalogService.activeMonthlyAmountRange(body.monthlyAmountRangeCode());
		activity.setMonthlyAmountRange(range);
		expectedActivities.save(activity);

		completeStep(request, OnboardingStep.EXPECTED_ACTIVITY);
		recordEvent(request, RequestEventType.EXPECTED_ACTIVITY_REGISTERED, "Movimiento esperado registrado");
		return riskAssessmentService.evaluate(request, range);
	}

	/** Review and submit: assigns the SOL-YYYY-NNNNN number and closes the request. */
	@Transactional
	public OnboardingRequestResponse submit(UUID requestId) {
		OnboardingRequest request = findInProgress(requestId);
		int stepsBeforeReview = OnboardingStep.REVIEW.ordinal();
		if (request.getCompletedSteps() < stepsBeforeReview) {
			throw new InvalidStateException("Complete the " + stepsBeforeReview + " previous steps before submitting");
		}

		Instant now = Instant.now();
		completeStep(request, OnboardingStep.REVIEW);
		request.setNumber(requests.nextNumber(now));
		request.setStatus(RequestStatus.COMPLETED);
		request.setSubmittedAt(now);
		recordEvent(request, RequestEventType.REQUEST_SUBMITTED, "Solicitud enviada",
				Map.of("number", request.getNumber()));
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
