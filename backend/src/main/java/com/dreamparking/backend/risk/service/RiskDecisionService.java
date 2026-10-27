package com.dreamparking.backend.risk.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.common.exception.InvalidStateException;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.onboarding.entity.RequestEvent;
import com.dreamparking.backend.onboarding.entity.enums.RequestEventType;
import com.dreamparking.backend.onboarding.entity.enums.RequestStatus;
import com.dreamparking.backend.onboarding.repository.RequestEventRepository;
import com.dreamparking.backend.risk.dto.RiskDecisionResponse;
import com.dreamparking.backend.risk.entity.RiskAssessment;
import com.dreamparking.backend.risk.entity.RiskAssessmentSignal;
import com.dreamparking.backend.risk.entity.RiskDecision;
import com.dreamparking.backend.risk.entity.ScoreModel;
import com.dreamparking.backend.risk.entity.enums.RiskDecisionType;
import com.dreamparking.backend.risk.repository.RiskAssessmentRepository;
import com.dreamparking.backend.risk.repository.RiskDecisionRepository;
import com.dreamparking.backend.risk.repository.ScoreModelRepository;

/**
 * Derivation rule (VDI-117): a submitted request whose score goes above the threshold of the model that scored it is
 * not approved automatically, it is held for enhanced review. Every decision, held or not, is written to the audit log
 * with its reason, date and time. The threshold is the one of the score model, so changing it is a new model version
 * and each decision keeps the one it was taken with.
 */
@Service
public class RiskDecisionService {

	private static final String SYSTEM = "SYSTEM";

	private final RiskAssessmentRepository assessments;

	private final ScoreModelRepository models;

	private final RiskDecisionRepository decisions;

	private final RequestEventRepository events;

	public RiskDecisionService(RiskAssessmentRepository assessments, ScoreModelRepository models,
			RiskDecisionRepository decisions, RequestEventRepository events) {
		this.assessments = assessments;
		this.models = models;
		this.decisions = decisions;
		this.events = events;
	}

	/**
	 * Decides what happens to the request with its current assessment, logs the decision and, above the threshold,
	 * moves the request to {@link RequestStatus#ENHANCED_REVIEW}. Without a score there is nothing to decide and no
	 * decision is logged, so a request is never held (or let through) on a guess.
	 */
	@Transactional
	public Optional<RiskDecisionResponse> decideOnSubmission(OnboardingRequest request) {
		Optional<RiskAssessment> current = assessments.findByRequestIdAndCurrentTrue(request.getId())
			.filter(assessment -> assessment.getScore() != null);
		if (current.isEmpty()) {
			return Optional.empty();
		}
		RiskAssessment assessment = current.get();
		ScoreModel model = models.findById(assessment.getModelVersion())
			.orElseThrow(() -> new InvalidStateException("Score model v" + assessment.getModelVersion() + " not found"));

		int score = assessment.getScore();
		boolean held = model.requiresEnhancedReview(score);
		RiskDecisionType type = held ? RiskDecisionType.ENHANCED_REVIEW : RiskDecisionType.AUTOMATIC_FLOW;
		RiskDecision decision = decisions
			.save(new RiskDecision(request, assessment, type, reason(model, assessment, held), model.reviewThreshold()));

		if (held) {
			// A request already waiting for the RNPN keeps that status: the decision is logged and the event is in the
			// timeline, and the review goes on from there once the RNPN answers.
			if (request.getStatus() == RequestStatus.COMPLETED) {
				request.setStatus(RequestStatus.ENHANCED_REVIEW);
			}
			RequestEvent event = new RequestEvent();
			event.setRequest(request);
			event.setType(RequestEventType.ENHANCED_REVIEW_REQUIRED);
			event.setDescription("Request held for enhanced review: score %d is above the threshold of %d (score model v%d)"
				.formatted(score, model.reviewThreshold(), model.getVersion()));
			event.setActor(SYSTEM);
			event.setData(Map.of("score", score, "threshold", model.reviewThreshold(), "modelVersion",
					model.getVersion()));
			events.save(event);
		}
		return Optional.of(RiskDecisionResponse.of(decision));
	}

	/** Audit log of the request, the oldest decision first. */
	@Transactional(readOnly = true)
	public List<RiskDecisionResponse> log(UUID requestId) {
		return decisions.findByRequestIdOrderByDecidedAtAscIdAsc(requestId).stream().map(RiskDecisionResponse::of).toList();
	}

	private static String reason(ScoreModel model, RiskAssessment assessment, boolean held) {
		int score = assessment.getScore();
		String compared = held ? "above" : "not above";
		String outcome = held ? "held for enhanced review" : "continues in the automatic flow";
		String main = mainSignals(assessment);
		return "Score %d is %s the enhanced-review threshold of %d (score model v%d): request %s%s.".formatted(score,
				compared, model.reviewThreshold(), model.getVersion(), outcome,
				main.isEmpty() ? "" : "; main signals: " + main);
	}

	/** The two signals that added the most points, like "amount (20) and typing pace (12)". */
	private static String mainSignals(RiskAssessment assessment) {
		return String.join(" and ", assessment.getSignals()
			.stream()
			.filter(signal -> signal.getPoints() != null && signal.getPoints() > 0)
			.sorted(Comparator.comparing(RiskAssessmentSignal::getPoints).reversed())
			.limit(2)
			.map(signal -> "%s (%d)".formatted(signal.getSignal().name().toLowerCase().replace('_', ' '),
					signal.getPoints()))
			.toList());
	}

}
