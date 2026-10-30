package com.dreamparking.backend.product.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.dreamparking.backend.common.exception.InvalidStateException;
import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.onboarding.repository.OnboardingRequestRepository;
import com.dreamparking.backend.onboarding.repository.RequestEventRepository;
import com.dreamparking.backend.product.dto.RegisterProductRequest;
import com.dreamparking.backend.product.entity.Product;
import com.dreamparking.backend.product.entity.ProductRequest;
import com.dreamparking.backend.product.repository.ProductRepository;
import com.dreamparking.backend.product.repository.ProductRequestRepository;

/** VDI-148: what the service refuses before saving anything. */
class ProductRequestServiceTests {

	private final OnboardingRequestRepository requests = mock(OnboardingRequestRepository.class);

	private final ProductRepository products = mock(ProductRepository.class);

	private final ProductRequestRepository productRequests = mock(ProductRequestRepository.class);

	private final ProductEligibilityService eligibility = mock(ProductEligibilityService.class);

	private final ProductRequestService service = new ProductRequestService(requests, products, productRequests,
			eligibility, mock(RequestEventRepository.class));

	private final UUID requestId = UUID.randomUUID();

	private final OnboardingRequest request = mock(OnboardingRequest.class);

	private final Product basic = product((short) 1, "AHORRO_BASICA", "Cuenta de ahorro básica");

	private final Product current = product((short) 3, "CORRIENTE", "Cuenta corriente");

	@Test
	void aProductOutsideTheEligibleOnesIsRefused() {
		found();
		when(eligibility.eligibleFor(request)).thenReturn(List.of(basic));

		assertThatThrownBy(() -> service.register(new RegisterProductRequest(requestId, "CORRIENTE")))
			.isInstanceOf(InvalidStateException.class)
			.hasMessage("La solicitud no califica para el producto Cuenta corriente");
		verify(productRequests, never()).saveAndFlush(any());
	}

	@Test
	void aSecondChoiceIsRefused() {
		found();
		when(eligibility.eligibleFor(request)).thenReturn(List.of(basic, current));
		ProductRequest chosen = mock(ProductRequest.class);
		when(chosen.getProduct()).thenReturn(basic);
		when(productRequests.findByRequestId(requestId)).thenReturn(Optional.of(chosen));

		assertThatThrownBy(() -> service.register(new RegisterProductRequest(requestId, "CORRIENTE")))
			.isInstanceOf(InvalidStateException.class)
			.hasMessageContaining("ya eligió un producto: Cuenta de ahorro básica");
		verify(productRequests, never()).saveAndFlush(any());
	}

	@Test
	void anUnknownProductIsNotFound() {
		when(requests.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
		when(products.findByCode("PLAZO_FIJO")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.register(new RegisterProductRequest(requestId, "PLAZO_FIJO")))
			.isInstanceOf(NotFoundException.class)
			.hasMessage("No existe el producto PLAZO_FIJO");
	}

	@Test
	void anUnknownRequestIsNotFound() {
		when(requests.findByIdForUpdate(requestId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.register(new RegisterProductRequest(requestId, "AHORRO_BASICA")))
			.isInstanceOf(NotFoundException.class)
			.hasMessage("No existe la solicitud " + requestId);
	}

	private void found() {
		when(request.getId()).thenReturn(requestId);
		when(requests.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
		when(products.findByCode("CORRIENTE")).thenReturn(Optional.of(current));
	}

	private static Product product(short id, String code, String name) {
		Product product = mock(Product.class);
		when(product.getId()).thenReturn(id);
		when(product.getCode()).thenReturn(code);
		when(product.getName()).thenReturn(name);
		return product;
	}

}
