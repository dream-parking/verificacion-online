package com.dreamparking.backend.product.service;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.common.exception.InvalidStateException;
import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.onboarding.entity.RequestEvent;
import com.dreamparking.backend.onboarding.entity.enums.RequestEventType;
import com.dreamparking.backend.onboarding.repository.OnboardingRequestRepository;
import com.dreamparking.backend.onboarding.repository.RequestEventRepository;
import com.dreamparking.backend.product.dto.ProductRequestResponse;
import com.dreamparking.backend.product.dto.RegisterProductRequest;
import com.dreamparking.backend.product.entity.Product;
import com.dreamparking.backend.product.entity.ProductRequest;
import com.dreamparking.backend.product.repository.ProductRepository;
import com.dreamparking.backend.product.repository.ProductRequestRepository;

/**
 * Registers the product an approved request chooses (VDI-148), reusing everything the request already has: the app
 * only sends the product code. A request chooses once, and only a product it qualifies for (the rule of
 * {@link ProductEligibilityService}). The choice goes to the timeline, in Spanish, as the console shows it.
 */
@Service
public class ProductRequestService {

	private final OnboardingRequestRepository requests;

	private final ProductRepository products;

	private final ProductRequestRepository productRequests;

	private final ProductEligibilityService eligibility;

	private final RequestEventRepository events;

	public ProductRequestService(OnboardingRequestRepository requests, ProductRepository products,
			ProductRequestRepository productRequests, ProductEligibilityService eligibility,
			RequestEventRepository events) {
		this.requests = requests;
		this.products = products;
		this.productRequests = productRequests;
		this.eligibility = eligibility;
		this.events = events;
	}

	/**
	 * Registers the choice.
	 * @throws NotFoundException when the request or the product does not exist
	 * @throws InvalidStateException when the request is not approved, does not qualify for the product, or already
	 * chose one
	 */
	@Transactional
	public ProductRequestResponse register(RegisterProductRequest body) {
		// Locked, so two choices sent at once for the same request are applied one after the other: the second one
		// finds the first. The unique request_id is the last guard.
		OnboardingRequest request = requests.findByIdForUpdate(body.requestId())
			.orElseThrow(() -> new NotFoundException("No existe la solicitud " + body.requestId()));
		Product product = products.findByCode(body.productCode())
			.orElseThrow(() -> new NotFoundException("No existe el producto " + body.productCode()));
		// The state errors (not approved, held, waiting for the RNPN) come from the eligibility rule.
		if (eligibility.eligibleFor(request).stream().noneMatch(eligible -> eligible.getId().equals(product.getId()))) {
			throw new InvalidStateException("La solicitud no califica para el producto " + product.getName());
		}
		productRequests.findByRequestId(request.getId()).ifPresent(chosen -> {
			throw new InvalidStateException(
					"La solicitud ya eligió un producto: " + chosen.getProduct().getName() + ". Solo se elige una vez");
		});

		ProductRequest saved = productRequests.saveAndFlush(new ProductRequest(request, product));
		RequestEvent event = new RequestEvent();
		event.setRequest(request);
		event.setType(RequestEventType.PRODUCT_REQUESTED);
		event.setDescription("Producto elegido: " + product.getName());
		event.setData(Map.of("productCode", product.getCode()));
		events.save(event);
		return ProductRequestResponse.of(saved);
	}

}
