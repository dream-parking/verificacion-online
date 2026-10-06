package com.dreamparking.backend.risk;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Riesgo", description = "Score de riesgo asignado a una solicitud")
public class RiskAssessmentController {

	private final RiskAssessmentService riskAssessmentService;

	public RiskAssessmentController(RiskAssessmentService riskAssessmentService) {
		this.riskAssessmentService = riskAssessmentService;
	}

	@ApiResponse(responseCode = "200", description = "Score de riesgo vigente")
	@Operation(summary = "Score de riesgo vigente de la solicitud")
	@ApiResponse(responseCode = "404", description = "La solicitud no existe o aún no tiene score", content = @Content)
	@GetMapping("/api/onboarding/requests/{requestId}/risk-assessment")
	public RiskAssessmentResponse current(@PathVariable UUID requestId) {
		return riskAssessmentService.current(requestId);
	}

}
