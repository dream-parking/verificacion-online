package com.dreamparking.backend.risk.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.common.exception.InvalidStateException;
import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.console.entity.ConsoleUser;
import com.dreamparking.backend.console.service.AccessAuditService;
import com.dreamparking.backend.console.service.ConsoleUserService;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.onboarding.entity.RequestEvent;
import com.dreamparking.backend.onboarding.entity.enums.RequestEventType;
import com.dreamparking.backend.onboarding.entity.enums.RequestStatus;
import com.dreamparking.backend.onboarding.repository.OnboardingRequestRepository;
import com.dreamparking.backend.onboarding.repository.RequestEventRepository;
import com.dreamparking.backend.risk.dto.EscalateRequest;
import com.dreamparking.backend.risk.dto.RiskDecisionResponse;
import com.dreamparking.backend.risk.entity.RiskAssessment;
import com.dreamparking.backend.risk.entity.RiskDecision;
import com.dreamparking.backend.risk.entity.ScoreModel;
import com.dreamparking.backend.risk.repository.RiskAssessmentRepository;
import com.dreamparking.backend.risk.repository.RiskDecisionRepository;
import com.dreamparking.backend.risk.repository.ScoreModelRepository;

/**
 * Escalation of a request held for enhanced review (VDI-143): an analyst sends it to a video call or to a branch. The
 * request moves to {@link RequestStatus#ESCALATED} with the channel, keeping everything it captured, and the escalation
 * is one more decision in the append-only audit log, with the channel, the reason, the analyst and the date and time.
 * The texts are in Spanish because the console shows them as they are.
 */
@Service
public class EscalationService {

	static final String AUDIT_ACTION = "ESCALATE_REQUEST";

	private static final String ANALYST = "ANALYST";

	private final OnboardingRequestRepository requests;

	private final RiskAssessmentRepository assessments;

	private final ScoreModelRepository models;

	private final RiskDecisionRepository decisions;

	private final RequestEventRepository events;

	private final ConsoleUserService users;

	private final AccessAuditService audit;

	public EscalationService(OnboardingRequestRepository requests, RiskAssessmentRepository assessments,
			ScoreModelRepository models, RiskDecisionRepository decisions, RequestEventRepository events,
			ConsoleUserService users, AccessAuditService audit) {
		this.requests = requests;
		this.assessments = assessments;
		this.models = models;
		this.decisions = decisions;
		this.events = events;
		this.users = users;
		this.audit = audit;
	}

	/**
	 * Escalates the request and logs the decision.
	 * @throws NotFoundException when the request does not exist
	 * @throws InvalidStateException when the request is not held for enhanced review
	 */
	@Transactional
	public RiskDecisionResponse escalate(UUID requestId, EscalateRequest body, UUID userId) {
		ConsoleUser analyst = users.activeUser(userId);
		// Locked, so two analysts escalating the same request at once do it one after the other: the second one finds
		// it already escalated.
		OnboardingRequest request = requests.findByIdForUpdate(requestId)
			.orElseThrow(() -> new NotFoundException("No existe la solicitud " + requestId));
		if (request.getStatus() != RequestStatus.ENHANCED_REVIEW) {
			throw new InvalidStateException("Solo se puede escalar una solicitud en revisión reforzada; esta está "
					+ statusLabel(request.getStatus()));
		}
		RiskAssessment assessment = assessments.findByRequestIdAndCurrentTrue(requestId)
			.orElseThrow(() -> new InvalidStateException("La solicitud " + requestId + " no tiene evaluación de riesgo"));
		ScoreModel model = models.findById(assessment.getModelVersion())
			.orElseThrow(() -> new InvalidStateException("No existe el modelo de score v" + assessment.getModelVersion()));

		String channel = body.channel().label();
		String reason = body.reason().trim();
		RiskDecision decision = decisions.save(RiskDecision.escalation(request, assessment, model.reviewThreshold(),
				body.channel(), "Escalada a " + channel + " por " + analyst.getFullName() + ": " + reason, analyst));
		request.escalate(body.channel());

		RequestEvent event = new RequestEvent();
		event.setRequest(request);
		event.setType(RequestEventType.REQUEST_ESCALATED);
		event.setDescription("Solicitud escalada a " + channel + ": " + reason);
		event.setActor(ANALYST);
		event.setData(Map.of("channel", body.channel().name(), "analyst", analyst.getFullName()));
		events.save(event);

		audit.record(analyst, AUDIT_ACTION, "onboarding_request", requestId.toString(), null);
		return RiskDecisionResponse.of(decision);
	}

	private static String statusLabel(RequestStatus status) {
		return switch (status) {
			case IN_PROGRESS -> "en progreso";
			case COMPLETED -> "completada";
			case ENHANCED_REVIEW -> "en revisión reforzada";
			case AWAITING_RNPN -> "en espera del RNPN";
			case ESCALATED -> "escalada";
			case ABANDONED -> "abandonada";
		};
	}

}
