package com.dreamparking.backend.onboarding;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.risk.RiskAssessmentResponse;

@RestController
@RequestMapping("/api/onboarding/requests")
public class OnboardingController {

	private final OnboardingService onboardingService;

	public OnboardingController(OnboardingService onboardingService) {
		this.onboardingService = onboardingService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public OnboardingRequestResponse start() {
		return onboardingService.start();
	}

	@GetMapping("/{requestId}")
	public OnboardingRequestResponse get(@PathVariable UUID requestId) {
		return onboardingService.get(requestId);
	}

	@PutMapping("/{requestId}/income")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void declareIncome(@PathVariable UUID requestId, @Valid @RequestBody IncomeDeclarationRequest body) {
		onboardingService.declareIncome(requestId, body);
	}

	/** Returns the risk score assigned from the declared monthly amount. */
	@PutMapping("/{requestId}/expected-activity")
	public RiskAssessmentResponse registerExpectedActivity(@PathVariable UUID requestId,
			@Valid @RequestBody ExpectedActivityRequest body) {
		return onboardingService.registerExpectedActivity(requestId, body);
	}

}
