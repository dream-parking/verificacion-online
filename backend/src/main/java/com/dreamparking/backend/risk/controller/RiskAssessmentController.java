package com.dreamparking.backend.risk.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.risk.dto.RiskAssessmentResponse;
import com.dreamparking.backend.risk.service.RiskAssessmentService;

@RestController
public class RiskAssessmentController {

	private final RiskAssessmentService riskAssessmentService;

	public RiskAssessmentController(RiskAssessmentService riskAssessmentService) {
		this.riskAssessmentService = riskAssessmentService;
	}

	@GetMapping("/api/onboarding/requests/{requestId}/risk-assessment")
	public RiskAssessmentResponse current(@PathVariable UUID requestId) {
		return riskAssessmentService.current(requestId);
	}

}
