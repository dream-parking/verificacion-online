package com.dreamparking.backend.risk.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dreamparking.backend.risk.entity.RiskDecision;
import com.dreamparking.backend.risk.entity.enums.RiskDecisionType;

/**
 * Entry of the audit log of risk decisions of a request (VDI-117): what was decided, why, and when. The score, the
 * threshold and the model version are the ones used at that moment.
 */
public record RiskDecisionResponse(Long id, RiskDecisionType decision, String reason, int score, int threshold,
		short modelVersion, @Schema(description = "Who decided: SYSTEM for the automatic rule") String decidedBy,
		Instant decidedAt) {

	public static RiskDecisionResponse of(RiskDecision decision) {
		return new RiskDecisionResponse(decision.getId(), decision.getDecision(), decision.getReason(),
				decision.getScore(), decision.getThreshold(), decision.getModelVersion(), decision.getDecidedBy(),
				decision.getDecidedAt());
	}

}
