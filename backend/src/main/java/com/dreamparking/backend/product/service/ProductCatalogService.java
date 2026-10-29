package com.dreamparking.backend.product.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.onboarding.repository.OnboardingRequestRepository;
import com.dreamparking.backend.product.dto.ProductResponse;

/** The products an applicant sees at the product step of the app (VDI-146). */
@Service
public class ProductCatalogService {

	private final OnboardingRequestRepository requests;

	private final ProductEligibilityService eligibility;

	public ProductCatalogService(OnboardingRequestRepository requests, ProductEligibilityService eligibility) {
		this.requests = requests;
		this.eligibility = eligibility;
	}

	@Transactional(readOnly = true)
	public List<ProductResponse> availableFor(UUID requestId) {
		return eligibility
			.eligibleFor(requests.findById(requestId)
				.orElseThrow(() -> new NotFoundException("Onboarding request not found: " + requestId)))
			.stream()
			.map(ProductResponse::of)
			.toList();
	}

}
