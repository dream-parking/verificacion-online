package com.dreamparking.backend.dossier.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

import com.dreamparking.backend.dossier.repository.BindingDossierRepository;
import com.dreamparking.backend.identity.repository.IdentityDocumentRepository;
import com.dreamparking.backend.liveness.repository.LivenessCheckRepository;
import com.dreamparking.backend.onboarding.repository.OnboardingRequestRepository;
import com.dreamparking.backend.onboarding.repository.PrivacyConsentRepository;
import com.dreamparking.backend.risk.repository.ScoreModelRepository;
import com.dreamparking.backend.risk.service.RiskAssessmentService;
import com.dreamparking.backend.risk.service.RiskDecisionService;
import com.dreamparking.backend.rnpn.repository.RnpnCheckRepository;

/** VDI-134: the retention date of a dossier is the date of the signature plus the years in the property. */
class BindingDossierRetentionTests {

	@ParameterizedTest
	@CsvSource({ "10, 2026-10-27T15:30:00Z, 2036-10-27T15:30:00Z", "5, 2026-10-27T15:30:00Z, 2031-10-27T15:30:00Z",
			"1, 2026-10-27T15:30:00Z, 2027-10-27T15:30:00Z",
			// Salvadoran time (UTC-6): at 03:00Z on 1 March it is still the evening of 28 February, and the year is added
			// there: 28 February 2028 in the evening, not 1 March 2028 as it would be counting in UTC.
			"1, 2027-03-01T03:00:00Z, 2028-02-29T03:00:00Z",
			// A signature on 29 February keeps the period counted in calendar years: it ends on 28 February.
			"1, 2024-02-29T18:00:00Z, 2025-02-28T18:00:00Z" })
	void theDateIsTheSignaturePlusTheYearsOfThePeriod(int years, Instant signedAt, Instant retainedUntil) {
		assertThat(service(years).retainedUntil(signedAt)).isEqualTo(retainedUntil);
	}

	@Test
	void aPeriodOfOneYearIsTheLeastThatIsAccepted() {
		assertThat(service(1)).isNotNull();
	}

	@ParameterizedTest
	@CsvSource({ "0", "-1" })
	void aPeriodShorterThanOneYearIsRefusedSoNothingIsKeptForNothing(int years) {
		assertThatThrownBy(() -> service(years)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("app.dossier.retention-years");
	}

	private static BindingDossierService service(int retentionYears) {
		return new BindingDossierService(mock(BindingDossierRepository.class), mock(OnboardingRequestRepository.class),
				mock(PrivacyConsentRepository.class), mock(IdentityDocumentRepository.class),
				mock(RnpnCheckRepository.class), mock(LivenessCheckRepository.class), mock(RiskAssessmentService.class),
				mock(RiskDecisionService.class), mock(ScoreModelRepository.class), JsonMapper.builder().build(),
				retentionYears);
	}

}
