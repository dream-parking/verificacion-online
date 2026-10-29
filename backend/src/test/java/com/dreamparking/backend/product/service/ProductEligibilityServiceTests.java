package com.dreamparking.backend.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.dreamparking.backend.common.exception.InvalidStateException;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.onboarding.entity.enums.RequestStatus;
import com.dreamparking.backend.product.entity.Product;
import com.dreamparking.backend.product.repository.ProductRepository;

/** VDI-146: only an approved request is offered products, and only those it qualifies for. */
class ProductEligibilityServiceTests {

	private final ProductRepository products = mock(ProductRepository.class);

	private final Product basic = product("AHORRO_BASICA");

	private final Product plus = product("AHORRO_PLUS");

	@Test
	void anApprovedRequestQualifiesForEveryActiveProductInCatalogOrder() {
		when(products.findByActiveTrueOrderByIdAsc()).thenReturn(List.of(basic, plus));

		assertThat(new ProductEligibilityService(products).eligibleFor(request(RequestStatus.COMPLETED)))
			.containsExactly(basic, plus);
	}

	@Test
	void aProductTheRequestDoesNotQualifyForIsLeftOut() {
		when(products.findByActiveTrueOrderByIdAsc()).thenReturn(List.of(basic, plus));
		ProductEligibilityService onlyBasic = new ProductEligibilityService(products) {
			@Override
			boolean qualifies(OnboardingRequest request, Product product) {
				return product == basic;
			}
		};

		assertThat(onlyBasic.eligibleFor(request(RequestStatus.COMPLETED))).containsExactly(basic);
	}

	@Test
	void anInactiveProductDoesNotQualify() {
		Product inactive = mock(Product.class);
		when(inactive.isActive()).thenReturn(false);

		assertThat(new ProductEligibilityService(products).qualifies(request(RequestStatus.COMPLETED), inactive))
			.isFalse();
	}

	@ParameterizedTest
	@EnumSource(value = RequestStatus.class, names = "COMPLETED", mode = EnumSource.Mode.EXCLUDE)
	void aRequestThatIsNotApprovedGetsAStateErrorNotAnEmptyList(RequestStatus status) {
		when(products.findByActiveTrueOrderByIdAsc()).thenReturn(List.of(basic));

		assertThatThrownBy(() -> new ProductEligibilityService(products).eligibleFor(request(status)))
			.isInstanceOf(InvalidStateException.class);
	}

	@Test
	void theWaitingStatesSayWhatTheyWaitFor() {
		ProductEligibilityService service = new ProductEligibilityService(products);

		assertThatThrownBy(() -> service.eligibleFor(request(RequestStatus.ENHANCED_REVIEW)))
			.hasMessageContaining("enhanced review");
		assertThatThrownBy(() -> service.eligibleFor(request(RequestStatus.AWAITING_RNPN))).hasMessageContaining("RNPN");
		assertThatThrownBy(() -> service.eligibleFor(request(RequestStatus.IN_PROGRESS)))
			.hasMessageContaining("Only submitted and approved");
	}

	private static OnboardingRequest request(RequestStatus status) {
		OnboardingRequest request = new OnboardingRequest();
		request.setStatus(status);
		return request;
	}

	private static Product product(String code) {
		Product product = mock(Product.class);
		when(product.getCode()).thenReturn(code);
		when(product.isActive()).thenReturn(true);
		return product;
	}

}
