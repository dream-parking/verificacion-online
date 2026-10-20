package com.dreamparking.backend.customer.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.customer.dto.CustomerRiskHistoryResponse;
import com.dreamparking.backend.customer.entity.Customer;
import com.dreamparking.backend.customer.repository.CustomerRepository;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.onboarding.repository.OnboardingRequestRepository;
import com.dreamparking.backend.risk.dto.RiskAssessmentResponse;
import com.dreamparking.backend.risk.dto.RiskDecisionResponse;
import com.dreamparking.backend.risk.repository.RiskAssessmentRepository;
import com.dreamparking.backend.risk.repository.RiskDecisionRepository;

/**
 * The risk signals of a customer found by their customer number (VDI-122): the score assessments and the decisions of
 * every request they made, grouped by request. Nothing is stored for it: the requests already point to the customer.
 */
@Service
public class CustomerRiskService {

	private final CustomerRepository customers;

	private final OnboardingRequestRepository requests;

	private final RiskAssessmentRepository assessments;

	private final RiskDecisionRepository decisions;

	public CustomerRiskService(CustomerRepository customers, OnboardingRequestRepository requests,
			RiskAssessmentRepository assessments, RiskDecisionRepository decisions) {
		this.customers = customers;
		this.requests = requests;
		this.assessments = assessments;
		this.decisions = decisions;
	}

	/** @throws NotFoundException when no customer has that number */
	@Transactional(readOnly = true)
	public CustomerRiskHistoryResponse byNumber(String customerNumber) {
		Customer customer = customers.findByNumber(customerNumber)
			.orElseThrow(() -> new NotFoundException("Customer not found for number " + customerNumber));
		UUID customerId = customer.getId();

		Map<UUID, List<RiskAssessmentResponse>> assessmentsByRequest = new LinkedHashMap<>();
		assessments.findByRequestCustomerIdOrderByEvaluatedAtAscIdAsc(customerId)
			.forEach(assessment -> assessmentsByRequest
				.computeIfAbsent(assessment.getRequest().getId(), id -> new java.util.ArrayList<>())
				.add(RiskAssessmentResponse.of(assessment)));
		Map<UUID, List<RiskDecisionResponse>> decisionsByRequest = new LinkedHashMap<>();
		decisions.findByRequestCustomerIdOrderByDecidedAtAscIdAsc(customerId)
			.forEach(decision -> decisionsByRequest
				.computeIfAbsent(decision.getRequest().getId(), id -> new java.util.ArrayList<>())
				.add(RiskDecisionResponse.of(decision)));

		List<CustomerRiskHistoryResponse.RequestRisk> perRequest = requests
			.findByCustomerIdOrderByStartedAtAscIdAsc(customerId)
			.stream()
			.map(request -> requestRisk(request, assessmentsByRequest, decisionsByRequest))
			.toList();
		return new CustomerRiskHistoryResponse(customerId, customer.getNumber(), perRequest);
	}

	private static CustomerRiskHistoryResponse.RequestRisk requestRisk(OnboardingRequest request,
			Map<UUID, List<RiskAssessmentResponse>> assessmentsByRequest,
			Map<UUID, List<RiskDecisionResponse>> decisionsByRequest) {
		return new CustomerRiskHistoryResponse.RequestRisk(request.getId(), request.getNumber(), request.getStatus(),
				request.getStartedAt(), request.getSubmittedAt(), assessmentsByRequest.getOrDefault(request.getId(), List.of()),
				decisionsByRequest.getOrDefault(request.getId(), List.of()));
	}

}
