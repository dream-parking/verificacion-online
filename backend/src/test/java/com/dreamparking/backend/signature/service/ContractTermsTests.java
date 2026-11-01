package com.dreamparking.backend.signature.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** VDI-130: the backend owns the contract text, so the fingerprint stored with a signature proves what was signed. */
class ContractTermsTests {

	private final ContractTerms terms = new ContractTerms("2026.1");

	@Test
	void loadsTheTextOfTheVersionInForce() {
		assertThat(terms.version()).isEqualTo("2026.1");
		assertThat(terms.text()).startsWith("CONTRATO DE APERTURA DE CUENTA").contains("Versión 2026.1");
	}

	@Test
	void theFingerprintIsTheSha256OfTheText() {
		assertThat(terms.fingerprint()).matches("[0-9a-f]{64}").isEqualTo(ContractTerms.fingerprintOf(terms.text()));
		// Known SHA-256 of "abc", to be sure it is SHA-256 in lower-case hex.
		assertThat(ContractTerms.fingerprintOf("abc"))
			.isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
	}

	@Test
	void theFingerprintDoesNotDependOnTheLineEndsOfTheFile() {
		assertThat(terms.text()).doesNotContain("\r");
	}

	@Test
	void anUnknownVersionStopsTheApplicationFromStarting() {
		assertThatThrownBy(() -> new ContractTerms("1999.9")).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("1999.9");
	}

}
