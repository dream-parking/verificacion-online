package com.dreamparking.backend.signature.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.dreamparking.backend.identity.service.DuiValidator;

/** VDI-130: the simulated signature service answers by DUI number, so QA and the app can try both results. */
class SimulatedContractSignerTests {

	private static final String REJECTED_DUI = "00000040-8";

	private final SimulatedContractSigner signer = new SimulatedContractSigner(Set.of(REJECTED_DUI));

	@Test
	void theTestDuiIsAValidDui() {
		assertThat(DuiValidator.hasValidCheckDigit(REJECTED_DUI)).isTrue();
	}

	@Test
	void anyOtherDuiSigns() {
		SigningResult result = signer.sign(query("01234567-8"));

		assertThat(result.signed()).isTrue();
		assertThat(result.rejectionReason()).isNull();
	}

	@Test
	void theConfiguredDuiIsRejectedWithAReason() {
		SigningResult result = signer.sign(query(REJECTED_DUI));

		assertThat(result.signed()).isFalse();
		assertThat(result.rejectionReason()).isEqualTo(SimulatedContractSigner.REJECTION_REASON);
	}

	@Test
	void itAlwaysSaysItIsTheSimulatedService() {
		assertThat(signer.provider()).isEqualTo(ContractSigner.SIMULATED);
	}

	@Test
	void aRejectionMustSayWhy() {
		assertThatThrownBy(() -> SigningResult.rejected(" ")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> SigningResult.rejected(null)).isInstanceOf(IllegalArgumentException.class);
	}

	private static SigningQuery query(String dui) {
		return new SigningQuery(UUID.randomUUID(), dui, "2026.1", "a".repeat(64));
	}

}
