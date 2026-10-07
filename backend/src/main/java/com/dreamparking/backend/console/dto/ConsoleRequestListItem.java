package com.dreamparking.backend.console.dto;

import java.time.Instant;
import java.util.UUID;

import com.dreamparking.backend.onboarding.entity.RequestListItem;
import com.dreamparking.backend.onboarding.entity.enums.RequestStatus;
import com.dreamparking.backend.risk.entity.enums.RiskLevel;

/** Row of the console request list. {@code number} is null until the request is submitted. */
public record ConsoleRequestListItem(UUID id, String number, String name, Instant date, String transactionTypeLabel,
		String monthlyAmountRangeLabel, RiskLevel riskLevel, RequestStatus status, short completedSteps) {

	public static ConsoleRequestListItem of(RequestListItem item) {
		return new ConsoleRequestListItem(item.getId(), item.getNumber(), item.getName(), item.getDate(),
				item.getTransactionTypeLabel(), item.getMonthlyAmountRangeLabel(), item.getRiskLevel(), item.getStatus(),
				item.getCompletedSteps() == null ? 0 : item.getCompletedSteps());
	}

}
