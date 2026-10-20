package com.dreamparking.backend.risk.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.catalog.entity.MonthlyAmountRange;
import com.dreamparking.backend.common.exception.InvalidStateException;
import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.identity.repository.IdentityDocumentRepository;
import com.dreamparking.backend.liveness.repository.LivenessCheckRepository;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.onboarding.entity.RequestEvent;
import com.dreamparking.backend.onboarding.entity.enums.RequestEventType;
import com.dreamparking.backend.onboarding.repository.RequestEventRepository;
import com.dreamparking.backend.onboarding.repository.RequestSignalsRepository;
import com.dreamparking.backend.risk.dto.RiskAssessmentResponse;
import com.dreamparking.backend.risk.entity.RiskAssessment;
import com.dreamparking.backend.risk.entity.RiskAssessmentSignal;
import com.dreamparking.backend.risk.entity.ScoreModel;
import com.dreamparking.backend.risk.entity.enums.RiskLevel;
import com.dreamparking.backend.risk.repository.RiskAssessmentRepository;
import com.dreamparking.backend.risk.repository.ScoreModelRepository;

/**
 * Risk score engine. Scores a request from every signal available at that moment with the {@link ScoreModel} in force
 * (see {@link ScoreEngine}): a score from 0 to 100, its risk level, and what each signal added, all kept with the
 * assessment. Without a declared amount there is nothing to score and the request stays not evaluated.
 */
@Service
public class RiskAssessmentService {

	private static final ZoneId EL_SALVADOR = ZoneId.of("America/El_Salvador");

	private final ScoreModelRepository models;

	private final RiskAssessmentRepository assessments;

	private final RequestEventRepository events;

	private final IdentityDocumentRepository identityDocuments;

	private final RequestSignalsRepository signals;

	private final LivenessCheckRepository livenessChecks;

	public RiskAssessmentService(ScoreModelRepository models, RiskAssessmentRepository assessments,
			RequestEventRepository events, IdentityDocumentRepository identityDocuments,
			RequestSignalsRepository signals, LivenessCheckRepository livenessChecks) {
		this.models = models;
		this.assessments = assessments;
		this.events = events;
		this.identityDocuments = identityDocuments;
		this.signals = signals;
		this.livenessChecks = livenessChecks;
	}

	/**
	 * Scores the request from its declared monthly amount range ({@code null} while not declared yet) and the other
	 * signals captured so far. It runs again when the request is submitted, so late signals count.
	 */
	@Transactional
	public RiskAssessmentResponse evaluate(OnboardingRequest request, MonthlyAmountRange range) {
		RiskAssessment assessment = new RiskAssessment();
		assessment.setRequest(request);
		if (range == null) {
			assessment.setLevel(RiskLevel.NOT_EVALUATED);
			assessment.setExplanation(
					"La solicitud sigue en progreso: todavía no hay un monto declarado que evaluar.");
		}
		else {
			ScoreModel model = models.findInForce()
				.orElseThrow(() -> new InvalidStateException("There is no score model in force"));
			var inputs = new ScoreEngine.Inputs(range, identityDocuments.findById(request.getId()).orElse(null),
					signals.findById(request.getId()).orElse(null), livenessChecks.findById(request.getId()).orElse(null),
					LocalDate.now(EL_SALVADOR));
			ScoreEngine.Result result = ScoreEngine.score(model, inputs);
			RiskLevel level = model.levelOf(result.score());
			assessment.setLevel(level);
			assessment.setEvaluatedValue(range.getMaxUsd() != null ? range.getMaxUsd() : range.getMinUsd());
			assessment.setExplanation(explanation(model, result, level));
			assessment.setScore(result.score(), model.getVersion(), result.contributions()
				.stream()
				.map(c -> new RiskAssessmentSignal(assessment, c.signal(), c.points(), c.maxPoints(), c.observation()))
				.toList());
		}

		assessments.retireCurrent(request.getId());
		assessments.save(assessment);
		request.setRiskLevel(assessment.getLevel());

		if (assessment.getScore() != null) {
			RequestEvent event = new RequestEvent();
			event.setRequest(request);
			event.setType(RequestEventType.SCORE_ASSIGNED);
			event.setDescription("Score de riesgo asignado: %d, %s (modelo v%d)".formatted(assessment.getScore(),
					levelName(assessment.getLevel()), assessment.getModelVersion()));
			event.setActor("SYSTEM");
			event.setData(Map.of("score", assessment.getScore(), "modelVersion", assessment.getModelVersion()));
			events.save(event);
		}
		return RiskAssessmentResponse.of(assessment);
	}

	/** Current assessment of the request, or empty while it has none. */
	@Transactional(readOnly = true)
	public Optional<RiskAssessmentResponse> findCurrent(UUID requestId) {
		return assessments.findByRequestIdAndCurrentTrue(requestId).map(RiskAssessmentResponse::of);
	}

	@Transactional(readOnly = true)
	public RiskAssessmentResponse current(UUID requestId) {
		return assessments.findByRequestIdAndCurrentTrue(requestId)
			.map(RiskAssessmentResponse::of)
			.orElseThrow(() -> new NotFoundException("Request " + requestId + " has no risk assessment"));
	}

	private static String explanation(ScoreModel model, ScoreEngine.Result result, RiskLevel level) {
		List<String> main = result.contributions()
			.stream()
			.filter(c -> c.points() != null && c.points() > 0)
			.sorted(Comparator.comparingInt(ScoreEngine.Contribution::points).reversed())
			.limit(2)
			.map(c -> "%s (%d)".formatted(c.signal().label().toLowerCase(), c.points()))
			.toList();
		String why = main.isEmpty() ? "ninguna señal sumó puntos" : "suman sobre todo " + String.join(" y ", main);
		return "Score %d de 100, riesgo %s (%s): %s.".formatted(result.score(), levelName(level), model.getName(), why);
	}

	private static String levelName(RiskLevel level) {
		return switch (level) {
			case LOW -> "bajo";
			case MEDIUM -> "medio";
			case HIGH -> "alto";
			case PENDING_REVIEW -> "pendiente de revisión";
			case NOT_EVALUATED -> "sin evaluar";
		};
	}

}
