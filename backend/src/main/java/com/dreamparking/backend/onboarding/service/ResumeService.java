package com.dreamparking.backend.onboarding.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.identity.entity.IdentityDocument;
import com.dreamparking.backend.identity.repository.IdentityDocumentRepository;
import com.dreamparking.backend.onboarding.dto.OnboardingRequestResponse;
import com.dreamparking.backend.onboarding.dto.ResumeResponse;
import com.dreamparking.backend.onboarding.dto.ResumeResponse.ResumeState;
import com.dreamparking.backend.onboarding.dto.ResumeResponse.StepProgress;
import com.dreamparking.backend.onboarding.dto.ResumeResponse.StepState;
import com.dreamparking.backend.onboarding.entity.ExpectedActivityVersion;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.onboarding.entity.RequestEvent;
import com.dreamparking.backend.onboarding.entity.enums.OnboardingStep;
import com.dreamparking.backend.onboarding.entity.enums.RequestEventType;
import com.dreamparking.backend.onboarding.entity.enums.RequestStatus;
import com.dreamparking.backend.onboarding.repository.ExpectedActivityRepository;
import com.dreamparking.backend.onboarding.repository.ExpectedActivityVersionRepository;
import com.dreamparking.backend.onboarding.repository.IncomeDeclarationRepository;
import com.dreamparking.backend.onboarding.repository.OnboardingRequestRepository;
import com.dreamparking.backend.onboarding.repository.RequestEventRepository;

/**
 * Saving a request half-way and continuing it from the last step (VDI-86). Every step is saved when the customer
 * completes it, so resuming only has to say where the request stopped and hand back what was saved. A request that has
 * been idle for longer than the resume window is not offered for resuming any more.
 */
@Service
public class ResumeService {

	/** The event that says when each step was saved. */
	private static final Map<OnboardingStep, RequestEventType> SAVED_BY = new EnumMap<>(Map.of(
			OnboardingStep.PRIVACY_NOTICE, RequestEventType.PRIVACY_ACCEPTED, OnboardingStep.BASIC_DATA,
			RequestEventType.BASIC_DATA_COMPLETED, OnboardingStep.IDENTITY_DOCUMENT,
			RequestEventType.IDENTITY_DOCUMENT_CONFIRMED, OnboardingStep.INCOME, RequestEventType.INCOME_REGISTERED,
			OnboardingStep.EXPECTED_ACTIVITY, RequestEventType.EXPECTED_ACTIVITY_REGISTERED, OnboardingStep.REVIEW,
			RequestEventType.REQUEST_SUBMITTED));

	private final OnboardingRequestRepository requests;

	private final IncomeDeclarationRepository incomeDeclarations;

	private final ExpectedActivityRepository expectedActivities;

	private final ExpectedActivityVersionRepository expectedActivityVersions;

	private final IdentityDocumentRepository identityDocuments;

	private final RequestEventRepository events;

	private final OnboardingService onboardingService;

	private final boolean identityDocumentRequired;

	private final Duration resumeWindow;

	public ResumeService(OnboardingRequestRepository requests, IncomeDeclarationRepository incomeDeclarations,
			ExpectedActivityRepository expectedActivities,
			ExpectedActivityVersionRepository expectedActivityVersions, IdentityDocumentRepository identityDocuments,
			RequestEventRepository events, OnboardingService onboardingService,
			@Value("${app.onboarding.identity-document-required:true}") boolean identityDocumentRequired,
			@Value("${app.onboarding.resume-window-days:7}") long resumeWindowDays) {
		this.requests = requests;
		this.incomeDeclarations = incomeDeclarations;
		this.expectedActivities = expectedActivities;
		this.expectedActivityVersions = expectedActivityVersions;
		this.identityDocuments = identityDocuments;
		this.events = events;
		this.onboardingService = onboardingService;
		this.identityDocumentRequired = identityDocumentRequired;
		this.resumeWindow = Duration.ofDays(resumeWindowDays);
	}

	/** Where the request stopped and what was saved; the state says whether it can be continued. */
	@Transactional(readOnly = true)
	public ResumeResponse resume(UUID requestId) {
		OnboardingRequest request = requests.findById(requestId)
			.orElseThrow(() -> new NotFoundException("Onboarding request not found: " + requestId));
		IdentityDocument document = identityDocuments.findById(requestId).orElse(null);
		Instant expiresAt = request.getLastActivityAt().plus(resumeWindow);
		ResumeState state = stateOf(request, expiresAt);
		List<StepProgress> steps = steps(request, document);
		OnboardingStep current = steps.stream()
			.filter(step -> step.state() == StepState.CURRENT)
			.map(StepProgress::step)
			.findFirst()
			.orElse(null);
		boolean resumable = state == ResumeState.RESUMABLE;
		return new ResumeResponse(requestId, state, request.getNumber(), firstName(request.getFirstNames()),
				resumable ? current : null, request.getCompletedSteps(), request.getLastActivityAt(),
				state == ResumeState.RESUMABLE || state == ResumeState.EXPIRED ? expiresAt : null, steps,
				resumable ? saved(request, document) : null);
	}

	/** The customer chose to start over: the request is left as abandoned so the app can open a new one. */
	@Transactional
	public OnboardingRequestResponse abandon(UUID requestId) {
		OnboardingRequest request = onboardingService.findInProgress(requestId);
		request.setStatus(RequestStatus.ABANDONED);
		RequestEvent event = new RequestEvent();
		event.setRequest(request);
		event.setType(RequestEventType.REQUEST_ABANDONED);
		event.setDescription("Solicitud abandonada: la persona decidió empezar de nuevo");
		event.setData(Map.of("completedSteps", request.getCompletedSteps()));
		events.save(event);
		return OnboardingRequestResponse.of(request);
	}

	private ResumeState stateOf(OnboardingRequest request, Instant expiresAt) {
		return switch (request.getStatus()) {
			// A request held for review, escalated or waiting for the RNPN is submitted as far as the app is concerned.
			case COMPLETED, ENHANCED_REVIEW, AWAITING_RNPN, ESCALATED -> ResumeState.COMPLETED;
			case ABANDONED -> ResumeState.ABANDONED;
			case IN_PROGRESS -> Instant.now().isAfter(expiresAt) ? ResumeState.EXPIRED : ResumeState.RESUMABLE;
		};
	}

	/**
	 * Every step with its state. The DUI step is skipped while it is optional and the customer did not confirm one, and
	 * the step after the ones saved is the current one.
	 */
	private List<StepProgress> steps(OnboardingRequest request, IdentityDocument document) {
		Map<RequestEventType, Instant> savedAt = new EnumMap<>(RequestEventType.class);
		for (RequestEvent event : events.findByRequestIdOrderByOccurredAt(request.getId())) {
			savedAt.put(event.getType(), event.getOccurredAt());
		}
		boolean documentConfirmed = document != null && document.isConfirmed();
		boolean skipDocument = !identityDocumentRequired && !documentConfirmed;
		boolean inProgress = request.getStatus() == RequestStatus.IN_PROGRESS;
		int saved = request.getCompletedSteps();
		List<StepProgress> steps = new ArrayList<>();
		boolean currentFound = false;
		for (OnboardingStep step : OnboardingStep.values()) {
			boolean isDocument = step == OnboardingStep.IDENTITY_DOCUMENT;
			StepState state;
			if (isDocument && skipDocument && (step.ordinal() < saved || saved == step.ordinal())) {
				state = StepState.SKIPPED;
			}
			else if (step.ordinal() < saved) {
				state = StepState.DONE;
			}
			else if (!currentFound && inProgress) {
				state = StepState.CURRENT;
				currentFound = true;
			}
			else {
				state = StepState.PENDING;
			}
			boolean wasSaved = state == StepState.DONE;
			steps.add(new StepProgress(step, state, wasSaved ? savedAt.get(SAVED_BY.get(step)) : null));
		}
		return steps;
	}

	private ResumeResponse.Saved saved(OnboardingRequest request, IdentityDocument document) {
		var basicData = request.getDui() == null ? null : new ResumeResponse.BasicData(request.getDui(),
				request.getFirstNames(), request.getLastNames(), request.getMobilePhone());
		var identityDocument = document == null ? null
				: new ResumeResponse.IdentityDocument(document.getStatus(), document.isConfirmed());
		var income = incomeDeclarations.findById(request.getId())
			.map(i -> new ResumeResponse.Income(i.getSource().getCode(), i.getSourceDetail(), i.getRange().getCode()))
			.orElse(null);
		var expectedActivity = expectedActivities.findById(request.getId())
			.map(a -> new ResumeResponse.ExpectedActivity(a.getTransactionType().getCode(),
					a.getMonthlyAmountRange().getCode(), profileVersion(request)))
			.orElse(null);
		return new ResumeResponse.Saved(basicData, identityDocument, income, expectedActivity);
	}

	/** The version of the declared profile in force (VDI-3): the highest one. */
	private short profileVersion(OnboardingRequest request) {
		return expectedActivityVersions.findFirstByRequest_IdOrderByVersionDesc(request.getId())
			.map(ExpectedActivityVersion::getVersion)
			.orElse((short) 1);
	}

	private static String firstName(String firstNames) {
		if (firstNames == null || firstNames.isBlank()) {
			return null;
		}
		return firstNames.trim().split("\\s+")[0];
	}

}
