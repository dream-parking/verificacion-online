package com.dreamparking.backend.rnpn.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.dreamparking.backend.identity.service.DuiValidator;
import com.dreamparking.backend.rnpn.entity.enums.RnpnField;

/** VDI-111: the simulated RNPN answers by DUI number, so QA and the app can try each result. */
class SimulatedRnpnClientTests {

	private static final String NO_MATCH_DUI = "00000020-4";

	private static final String UNAVAILABLE_DUI = "00000030-1";

	private final SimulatedRnpnClient client = new SimulatedRnpnClient(Set.of(NO_MATCH_DUI), Set.of(UNAVAILABLE_DUI));

	@Test
	void theTestDuisAreValidNumbers() {
		assertThat(DuiValidator.hasValidCheckDigit(NO_MATCH_DUI)).isTrue();
		assertThat(DuiValidator.hasValidCheckDigit(UNAVAILABLE_DUI)).isTrue();
	}

	@Test
	void anyOtherDuiMatches() throws Exception {
		RnpnResult result = client.check(query("01234567-8"));

		assertThat(result.matches()).isTrue();
		assertThat(result.mismatchedFields()).isEmpty();
	}

	@Test
	void theNoMatchDuiSaysWhichDataDiffers() throws Exception {
		RnpnResult result = client.check(query(NO_MATCH_DUI));

		assertThat(result.matches()).isFalse();
		assertThat(result.mismatchedFields()).containsExactly(RnpnField.LAST_NAMES, RnpnField.BIRTH_DATE);
	}

	@Test
	void theUnavailableDuiDoesNotAnswer() {
		assertThatThrownBy(() -> client.check(query(UNAVAILABLE_DUI))).isInstanceOf(RnpnException.class);
	}

	@Test
	void theProviderIsLabelledAsSimulated() {
		assertThat(client.provider()).isEqualTo("simulated");
	}

	@Test
	void aResultThatDoesNotMatchMustNameTheFields() {
		assertThatThrownBy(() -> RnpnResult.noMatch(java.util.List.of())).isInstanceOf(IllegalArgumentException.class);
	}

	private static RnpnQuery query(String dui) {
		return new RnpnQuery(dui, "Ana Sofía", "Pérez López", LocalDate.of(1991, 3, 14));
	}

}
