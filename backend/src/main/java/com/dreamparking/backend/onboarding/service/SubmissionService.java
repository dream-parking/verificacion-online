package com.dreamparking.backend.onboarding.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.dreamparking.backend.onboarding.dto.OnboardingRequestResponse;
import com.dreamparking.backend.rnpn.client.RnpnQuery;
import com.dreamparking.backend.rnpn.service.RnpnService;

/**
 * Submits a request: checks it can be submitted, asks the RNPN about the confirmed DUI data and then closes the request
 * in one transaction (VDI-111). The RNPN is asked in between, outside any transaction, because its answer can be slow
 * and it is retried: holding a database connection meanwhile would starve the small pool. This class is deliberately
 * not transactional for that reason.
 */
@Service
public class SubmissionService {

	private final OnboardingService onboardingService;

	private final RnpnService rnpnService;

	public SubmissionService(OnboardingService onboardingService, RnpnService rnpnService) {
		this.onboardingService = onboardingService;
		this.rnpnService = rnpnService;
	}

	public OnboardingRequestResponse submit(UUID requestId) {
		Optional<RnpnQuery> query = onboardingService.prepareSubmission(requestId);
		RnpnService.Answer answer = query.map(rnpnService::check).orElse(null);
		return onboardingService.submit(requestId, answer);
	}

}
