package com.dreamparking.backend.console.dto;

import java.time.Instant;
import java.util.UUID;

import com.dreamparking.backend.onboarding.entity.enums.RequestStatus;
import com.dreamparking.backend.risk.entity.enums.RiskLevel;

/** Row of the console request list. {@code number} is null until the request is submitted. */
public record ConsoleRequestListItem(UUID id, String number, String name, Instant date, String transactionTypeLabel,
		String monthlyAmountRangeLabel, RiskLevel riskLevel, RequestStatus status, short completedSteps) {
}
