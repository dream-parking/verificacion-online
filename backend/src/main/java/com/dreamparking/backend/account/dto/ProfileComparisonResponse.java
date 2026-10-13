package com.dreamparking.backend.account.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dreamparking.backend.account.entity.enums.ProfileDeviationType;

/**
 * Declared profile against the real activity of one month (VDI-3). {@code withinProfile} is true when there is no
 * deviation. Each deviation says what was declared, what really happened, the limit with the tolerance applied and the
 * alert it raised.
 */
public record ProfileComparisonResponse(UUID accountId, UUID requestId,
		@Schema(description = "Mes comparado (calendario de El Salvador)", example = "2026-10") String month,
		@Schema(description = "Margen sobre lo declarado antes de considerarlo una desviación", example = "20") int tolerancePercent,
		Declared declared, Actual actual, List<Deviation> deviations, boolean withinProfile) {

	/** What the customer declared in the onboarding request; null fields were not declared. */
	@Schema(name = "DeclaredProfile")
	public record Declared(List<String> transactionTypeCodes, String monthlyAmountRangeCode,
			String monthlyAmountRangeLabel, BigDecimal rangeMinUsd, BigDecimal rangeMaxUsd, BigDecimal monthlyAmountUsd,
			Short monthlyTransactionCount, BigDecimal monthlyOutflowUsd) {
	}

	/** What really moved in the month. */
	@Schema(name = "ActualActivity")
	public record Actual(BigDecimal inflowUsd, int inflowCount, BigDecimal outflowUsd, int outflowCount,
			List<TypeTotal> inflowByTransactionType) {
	}

	/** Money received of one type; {@code transactionTypeCode} null means not classified by the core. */
	@Schema(name = "ActualActivityByType")
	public record TypeTotal(String transactionTypeCode, BigDecimal amountUsd, int count, boolean declared) {
	}

	/**
	 * A difference with the declared profile. {@code alertId} and {@code detectedAt} are null only while the deviation
	 * has not been recorded yet.
	 */
	@Schema(name = "ProfileDeviation")
	public record Deviation(ProfileDeviationType type, String alertTypeCode, BigDecimal declaredValue,
			BigDecimal actualValue, BigDecimal limitValue, String detail, UUID alertId, Instant detectedAt) {
	}

}
