package com.dreamparking.backend.risk.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.catalog.entity.MonthlyAmountRange;
import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.onboarding.entity.RequestEvent;
import com.dreamparking.backend.onboarding.entity.enums.RequestEventType;
import com.dreamparking.backend.onboarding.repository.RequestEventRepository;
import com.dreamparking.backend.risk.dto.RiskAssessmentResponse;
import com.dreamparking.backend.risk.entity.RiskAssessment;
import com.dreamparking.backend.risk.entity.ScoreRule;
import com.dreamparking.backend.risk.entity.enums.RiskLevel;
import com.dreamparking.backend.risk.repository.RiskAssessmentRepository;
import com.dreamparking.backend.risk.repository.ScoreRuleRepository;

/**
 * Risk score engine. Applies rule R-01: a declared monthly amount range that does not go above the threshold scores
 * {@link RiskLevel#LOW}; anything else stays pending review, since R-01 only assigns low risk.
 * Same behavior as the {@code evaluate_risk} database function.
 */
@Service
public class RiskAssessmentService {

	static final String LOW_AMOUNT_RULE = "R-01";

	private static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("500");

	private final ScoreRuleRepository rules;

	private final RiskAssessmentRepository assessments;

	private final RequestEventRepository events;

	public RiskAssessmentService(ScoreRuleRepository rules, RiskAssessmentRepository assessments,
			RequestEventRepository events) {
		this.rules = rules;
		this.assessments = assessments;
		this.events = events;
	}

	/**
	 * Scores the request from its declared monthly amount range ({@code null} while not declared yet). The value
	 * evaluated is the top of the range; a range with no top ("more than…") is never low risk.
	 */
	@Transactional
	public RiskAssessmentResponse evaluate(OnboardingRequest request, MonthlyAmountRange range) {
		ScoreRule rule = rules.findInForce(LOW_AMOUNT_RULE).orElse(null);

		RiskAssessment assessment = new RiskAssessment();
		assessment.setRequest(request);
		if (range == null) {
			assessment.setLevel(RiskLevel.NOT_EVALUATED);
			assessment.setExplanation(
					"La solicitud sigue en progreso: todavía no hay un monto declarado que evaluar.");
		}
		else {
			assessment.setEvaluatedValue(range.getMaxUsd() != null ? range.getMaxUsd() : range.getMinUsd());
			if (rule != null && range.getMaxUsd() != null && range.getMaxUsd().compareTo(rule.getThreshold()) <= 0) {
				assessment.setLevel(rule.getResultIfMatched());
				assessment.setRule(rule);
				assessment.setExplanation(
						"Monto mensual declarado en el rango «%s», que no supera el umbral de USD %s, riesgo bajo."
							.formatted(range.getLabel(), usd(rule.getThreshold())));
			}
			else {
				assessment.setLevel(rule == null ? RiskLevel.PENDING_REVIEW : rule.getResultIfNotMatched());
				assessment.setExplanation(("Monto mensual declarado en el rango «%s», que supera el umbral de USD %s. "
						+ "La regla vigente solo asigna riesgo bajo, así que este caso queda pendiente de evaluación.")
					.formatted(range.getLabel(), usd(rule == null ? DEFAULT_THRESHOLD : rule.getThreshold())));
			}
		}

		assessments.retireCurrent(request.getId());
		assessments.save(assessment);
		request.setRiskLevel(assessment.getLevel());

		if (assessment.getRule() != null) {
			RequestEvent event = new RequestEvent();
			event.setRequest(request);
			event.setType(RequestEventType.SCORE_ASSIGNED);
			event.setDescription("Score de riesgo asignado: bajo (regla " + LOW_AMOUNT_RULE + ")");
			event.setActor("SYSTEM");
			event.setData(Map.of("rule", LOW_AMOUNT_RULE, "range", range.getCode()));
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

	/** Whole dollars with thousands separators, like {@code to_char(v, 'FM999,999,990')} in the database. */
	private static String usd(BigDecimal amount) {
		DecimalFormat format = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
		format.setRoundingMode(RoundingMode.HALF_UP);
		return format.format(amount);
	}

}
