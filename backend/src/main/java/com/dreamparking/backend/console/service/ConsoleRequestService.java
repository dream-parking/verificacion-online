package com.dreamparking.backend.console.service;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.common.dto.PageResponse;
import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.console.dto.ConsoleRequestDetail;
import com.dreamparking.backend.console.dto.ConsoleRequestListItem;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.onboarding.entity.RequestListItem;
import com.dreamparking.backend.onboarding.entity.RequestSignals;
import com.dreamparking.backend.onboarding.entity.enums.RequestStatus;
import com.dreamparking.backend.onboarding.repository.ExpectedActivityRepository;
import com.dreamparking.backend.onboarding.repository.IncomeDeclarationRepository;
import com.dreamparking.backend.onboarding.repository.OnboardingRequestRepository;
import com.dreamparking.backend.onboarding.repository.RequestEventRepository;
import com.dreamparking.backend.onboarding.repository.RequestListItemRepository;
import com.dreamparking.backend.onboarding.repository.RequestSignalsRepository;
import com.dreamparking.backend.onboarding.repository.RequestStepRepository;
import com.dreamparking.backend.risk.entity.enums.RiskLevel;
import com.dreamparking.backend.risk.service.RiskAssessmentService;

/** Read side of the console: request list and detail. */
@Service
@Transactional(readOnly = true)
public class ConsoleRequestService {

	static final int MAX_PAGE_SIZE = 100;

	private final RequestListItemRepository listItems;

	private final OnboardingRequestRepository requests;

	private final IncomeDeclarationRepository incomeDeclarations;

	private final ExpectedActivityRepository expectedActivities;

	private final RequestSignalsRepository signals;

	private final RequestStepRepository steps;

	private final RequestEventRepository events;

	private final RiskAssessmentService riskAssessments;

	public ConsoleRequestService(RequestListItemRepository listItems, OnboardingRequestRepository requests,
			IncomeDeclarationRepository incomeDeclarations, ExpectedActivityRepository expectedActivities,
			RequestSignalsRepository signals, RequestStepRepository steps, RequestEventRepository events,
			RiskAssessmentService riskAssessments) {
		this.listItems = listItems;
		this.requests = requests;
		this.incomeDeclarations = incomeDeclarations;
		this.expectedActivities = expectedActivities;
		this.signals = signals;
		this.steps = steps;
		this.events = events;
		this.riskAssessments = riskAssessments;
	}

	/** Newest first; {@code query} matches the applicant's name or the request number. */
	public PageResponse<ConsoleRequestListItem> list(RequestStatus status, RiskLevel riskLevel, String query,
			int page, int size) {
		Specification<RequestListItem> spec = (root, q, cb) -> cb.conjunction();
		if (status != null) {
			spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
		}
		if (riskLevel != null) {
			spec = spec.and((root, q, cb) -> cb.equal(root.get("riskLevel"), riskLevel));
		}
		if (query != null && !query.isBlank()) {
			String like = "%" + escapeLike(query.trim().toLowerCase()) + "%";
			spec = spec.and((root, q, cb) -> cb.or(cb.like(cb.lower(root.get("name")), like, '\\'),
					cb.like(cb.lower(root.get("number")), like, '\\')));
		}
		PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
				Sort.by(Sort.Order.desc("date"), Sort.Order.asc("id")));
		return PageResponse.of(listItems.findAll(spec, pageable), ConsoleRequestListItem::of);
	}

	public ConsoleRequestDetail detail(UUID requestId) {
		OnboardingRequest request = requests.findById(requestId)
			.orElseThrow(() -> new NotFoundException("Onboarding request not found: " + requestId));

		var applicant = new ConsoleRequestDetail.Applicant(request.getFirstNames(), request.getLastNames(),
				request.getDui(), request.getMobilePhone());
		var income = incomeDeclarations.findById(requestId)
			.map(d -> new ConsoleRequestDetail.Income(d.getSource().getCode(), d.getSource().getLabel(),
					d.getSourceDetail(), d.getRange().getCode(), d.getRange().getLabel(), d.getRegisteredAt(),
					d.getUpdatedAt()))
			.orElse(null);
		var expectedActivity = expectedActivities.findById(requestId)
			.map(a -> new ConsoleRequestDetail.ExpectedActivity(a.getTransactionType().getCode(),
					a.getTransactionType().getLabel(), a.getMonthlyAmountRange().getCode(),
					a.getMonthlyAmountRange().getLabel(), a.getRegisteredAt(), a.getUpdatedAt()))
			.orElse(null);
		var risk = riskAssessments.findCurrent(requestId).orElse(null);
		var signalsView = signals.findById(requestId)
			.filter(s -> s.getIp() != null || s.getDeviceFingerprint() != null)
			.map(ConsoleRequestService::toSignals)
			.orElse(null);
		var stepTimes = steps.findByRequestIdOrderByStartedAt(requestId)
			.stream()
			.map(s -> new ConsoleRequestDetail.StepTime(s.getStep(), s.getStartedAt(), s.getCompletedAt(),
					s.getDurationSeconds(), s.getAttempts()))
			.toList();
		var timeline = events.findByRequestIdOrderByOccurredAt(requestId)
			.stream()
			.map(e -> new ConsoleRequestDetail.TimelineEntry(e.getType(), e.getDescription(), e.getActor(),
					e.getOccurredAt()))
			.toList();

		return new ConsoleRequestDetail(request.getId(), request.getNumber(), request.getStatus(),
				request.getCompletedSteps(), request.getRiskLevel(), request.getStartedAt(),
				request.getSubmittedAt(), applicant, income, expectedActivity, risk, signalsView, stepTimes,
				timeline);
	}

	private static ConsoleRequestDetail.Signals toSignals(RequestSignals s) {
		return new ConsoleRequestDetail.Signals(s.getIp() == null ? null : s.getIp().getHostAddress(),
				s.getApproximateLocation(), s.getDeviceFingerprint(), s.getDevice(), s.getTypingSpeedCpm(),
				s.getTypingPace(), s.getNightTime(), s.getTotalDurationSeconds(), s.getRequestsFromSameDevice());
	}

	private static String escapeLike(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

}
