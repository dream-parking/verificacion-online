package com.dreamparking.backend.onboarding.controller;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.onboarding.dto.BasicDataRequest;
import com.dreamparking.backend.onboarding.dto.ExpectedActivityRequest;
import com.dreamparking.backend.onboarding.dto.IncomeDeclarationRequest;
import com.dreamparking.backend.onboarding.dto.OnboardingRequestResponse;
import com.dreamparking.backend.onboarding.dto.PrivacyConsentRequest;
import com.dreamparking.backend.onboarding.dto.SessionResponse;
import com.dreamparking.backend.onboarding.dto.StartSessionRequest;
import com.dreamparking.backend.onboarding.service.OnboardingService;
import com.dreamparking.backend.onboarding.service.OnboardingSessionService;
import com.dreamparking.backend.risk.dto.RiskAssessmentResponse;

/** API used by the mobile app, one endpoint per onboarding step. */
@RestController
@RequestMapping("/api/onboarding/requests")
public class OnboardingController {

	private final OnboardingService onboardingService;

	private final OnboardingSessionService sessionService;

	public OnboardingController(OnboardingService onboardingService, OnboardingSessionService sessionService) {
		this.onboardingService = onboardingService;
		this.sessionService = sessionService;
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

	@PostMapping("/{requestId}/sessions")
	@ResponseStatus(HttpStatus.CREATED)
	public SessionResponse startSession(@PathVariable UUID requestId, @Valid @RequestBody StartSessionRequest body,
			HttpServletRequest http) {
		return sessionService.start(requestId, body, clientIp(http), http.getHeader(HttpHeaders.USER_AGENT));
	}

	@PostMapping("/{requestId}/sessions/{sessionId}/end")
	public SessionResponse endSession(@PathVariable UUID requestId, @PathVariable UUID sessionId) {
		return sessionService.end(requestId, sessionId);
	}

	@PutMapping("/{requestId}/privacy-consent")
	public OnboardingRequestResponse acceptPrivacyNotice(@PathVariable UUID requestId,
			@Valid @RequestBody PrivacyConsentRequest body, HttpServletRequest http) {
		return onboardingService.acceptPrivacyNotice(requestId, clientIp(http));
	}

	@PutMapping("/{requestId}/basic-data")
	public OnboardingRequestResponse registerBasicData(@PathVariable UUID requestId,
			@Valid @RequestBody BasicDataRequest body) {
		return onboardingService.registerBasicData(requestId, body);
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

	@PostMapping("/{requestId}/submit")
	public OnboardingRequestResponse submit(@PathVariable UUID requestId) {
		return onboardingService.submit(requestId);
	}

	/** Client address as seen by the servlet container (a literal IP, so no DNS lookup happens). */
	private static InetAddress clientIp(HttpServletRequest http) {
		try {
			return InetAddress.getByName(http.getRemoteAddr());
		}
		catch (UnknownHostException ex) {
			throw new IllegalStateException("Invalid client address: " + http.getRemoteAddr(), ex);
		}
	}

}
