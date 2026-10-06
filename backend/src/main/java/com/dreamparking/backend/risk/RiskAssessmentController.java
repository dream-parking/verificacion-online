package com.dreamparking.backend.risk;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

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
