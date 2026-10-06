package com.dreamparking.backend.onboarding.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.dreamparking.backend.onboarding.entity.RequestListItem;
import com.dreamparking.backend.onboarding.entity.enums.RequestStatus;
import com.dreamparking.backend.risk.entity.enums.RiskLevel;

/** Row of the console request list. {@code date} is the submission date, or the last activity while in progress. */
public record RequestListItemResponse(UUID id, String number, String name, Instant date, String transactionType,
		BigDecimal monthlyAmountUsd, RiskLevel riskLevel, RequestStatus status, Short completedSteps) {

	public static RequestListItemResponse of(RequestListItem item) {
		return new RequestListItemResponse(item.getId(), item.getNumber(), item.getName(), item.getDate(),
				item.getTransactionTypeLabel(), item.getMonthlyAmountUsd(), item.getRiskLevel(), item.getStatus(),
				item.getCompletedSteps());
	}

}
