package com.dreamparking.backend.customer.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.dreamparking.backend.onboarding.entity.enums.RequestStatus;
import com.dreamparking.backend.risk.dto.RiskAssessmentResponse;
import com.dreamparking.backend.risk.dto.RiskDecisionResponse;

/**
 * The risk signals of a customer, found by their customer number (VDI-122): for each of their requests, the oldest
 * first, every score assessment (the oldest first, the last one is the one in force) and the decisions taken when the
 * request was submitted (VDI-117).
 */
public record CustomerRiskHistoryResponse(UUID customerId, String customerNumber, List<RequestRisk> requests) {

	/** The risk of one request of the customer. {@code number} is null until the request is submitted. */
	public record RequestRisk(UUID requestId, String number, RequestStatus status, Instant startedAt,
			Instant submittedAt, List<RiskAssessmentResponse> assessments, List<RiskDecisionResponse> decisions) {
	}

}
