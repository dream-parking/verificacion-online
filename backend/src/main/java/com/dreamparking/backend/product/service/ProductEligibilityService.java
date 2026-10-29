package com.dreamparking.backend.product.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.dreamparking.backend.common.exception.InvalidStateException;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.onboarding.entity.enums.RequestStatus;
import com.dreamparking.backend.product.entity.Product;
import com.dreamparking.backend.product.repository.ProductRepository;

/**
 * Which products a request qualifies for (VDI-146). Kept apart from the controller so the rule can change without
 * touching it. PROPOSED rule, to confirm with the PO: only an approved request ({@code COMPLETED}) can choose, and it
 * qualifies for every active product. A request that is not approved gets an error, never an empty list.
 */
@Service
public class ProductEligibilityService {

	private final ProductRepository products;

	public ProductEligibilityService(ProductRepository products) {
		this.products = products;
	}

	/** The active products the request qualifies for, in catalog order. */
	public List<Product> eligibleFor(OnboardingRequest request) {
		checkApproved(request);
		return products.findByActiveTrueOrderByIdAsc().stream().filter(product -> qualifies(request, product)).toList();
	}

	/** The rule for one product; today every active product is open to an approved request. */
	boolean qualifies(OnboardingRequest request, Product product) {
		return product.isActive();
	}

	/** Same wording as the contract signature: the states that wait for something say so. */
	private static void checkApproved(OnboardingRequest request) {
		if (request.getStatus() == RequestStatus.ENHANCED_REVIEW) {
			throw new InvalidStateException("The request is held for enhanced review and cannot choose a product");
		}
		if (request.getStatus() == RequestStatus.AWAITING_RNPN) {
			throw new InvalidStateException("The request is waiting for the RNPN and cannot choose a product");
		}
		if (request.getStatus() != RequestStatus.COMPLETED) {
			throw new InvalidStateException("Only submitted and approved requests can choose a product");
		}
	}

}
