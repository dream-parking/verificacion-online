package com.dreamparking.backend.onboarding.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.catalog.entity.IncomeSource;
import com.dreamparking.backend.catalog.service.CatalogService;
import com.dreamparking.backend.common.exception.InvalidInputException;
import com.dreamparking.backend.common.exception.InvalidStateException;
import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.onboarding.dto.ExpectedActivityRequest;
import com.dreamparking.backend.onboarding.dto.IncomeDeclarationRequest;
import com.dreamparking.backend.onboarding.dto.OnboardingRequestResponse;
import com.dreamparking.backend.onboarding.entity.ExpectedActivity;
import com.dreamparking.backend.onboarding.entity.IncomeDeclaration;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.onboarding.entity.RequestEvent;
import com.dreamparking.backend.onboarding.entity.enums.RequestEventType;
import com.dreamparking.backend.onboarding.entity.enums.RequestStatus;
import com.dreamparking.backend.onboarding.repository.ExpectedActivityRepository;
import com.dreamparking.backend.onboarding.repository.IncomeDeclarationRepository;
import com.dreamparking.backend.onboarding.repository.OnboardingRequestRepository;
import com.dreamparking.backend.onboarding.repository.RequestEventRepository;
import com.dreamparking.backend.risk.dto.RiskAssessmentResponse;
import com.dreamparking.backend.risk.service.RiskAssessmentService;

/** Steps of the mobile onboarding flow. */
@Service
public class OnboardingService {

	private static final String OTHER_INCOME_SOURCE = "OTRO";

	private final OnboardingRequestRepository requests;

	private final IncomeDeclarationRepository incomeDeclarations;

	private final ExpectedActivityRepository expectedActivities;

	private final RequestEventRepository events;

	private final CatalogService catalogService;

	private final RiskAssessmentService riskAssessmentService;

	public OnboardingService(OnboardingRequestRepository requests, IncomeDeclarationRepository incomeDeclarations,
			ExpectedActivityRepository expectedActivities, RequestEventRepository events,
			CatalogService catalogService, RiskAssessmentService riskAssessmentService) {
		this.requests = requests;
		this.incomeDeclarations = incomeDeclarations;
		this.expectedActivities = expectedActivities;
		this.events = events;
		this.catalogService = catalogService;
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

	/** Step 2: saves (or replaces) the declared income source and range. */
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

		touch(request);
		recordEvent(request, RequestEventType.INCOME_REGISTERED, "Ingresos registrados");
	}

	/** Step 3: saves (or replaces) the expected activity and scores the request's risk from the monthly amount. */
	@Transactional
	public RiskAssessmentResponse registerExpectedActivity(UUID requestId, ExpectedActivityRequest body) {
		OnboardingRequest request = findInProgress(requestId);

		ExpectedActivity activity = expectedActivities.findById(requestId).orElseGet(ExpectedActivity::new);
		activity.setRequest(request);
		activity.setTransactionType(catalogService.activeTransactionType(body.transactionTypeCode()));
		activity.setMonthlyAmountUsd(body.monthlyAmountUsd());
		expectedActivities.save(activity);

		touch(request);
		recordEvent(request, RequestEventType.EXPECTED_ACTIVITY_REGISTERED, "Movimiento esperado registrado");
		return riskAssessmentService.evaluate(request, body.monthlyAmountUsd());
	}

	private OnboardingRequest find(UUID requestId) {
		return requests.findById(requestId)
			.orElseThrow(() -> new NotFoundException("Onboarding request not found: " + requestId));
	}

	private OnboardingRequest findInProgress(UUID requestId) {
		OnboardingRequest request = find(requestId);
		if (request.getStatus() != RequestStatus.IN_PROGRESS) {
			throw new InvalidStateException("Onboarding request " + requestId + " is " + request.getStatus());
		}
		return request;
	}

	private void touch(OnboardingRequest request) {
		request.setLastActivityAt(Instant.now());
	}

	private void recordEvent(OnboardingRequest request, RequestEventType type, String description) {
		RequestEvent event = new RequestEvent();
		event.setRequest(request);
		event.setType(type);
		event.setDescription(description);
		events.save(event);
	}

}
