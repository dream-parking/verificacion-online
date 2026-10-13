package com.dreamparking.backend.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.dreamparking.backend.common.exception.InvalidInputException;
import com.dreamparking.backend.onboarding.dto.CaptureSignalsRequest;
import com.dreamparking.backend.onboarding.entity.enums.LocationStatus;

/** VDI-67: only addresses a provider can locate are sent to it. */
class SignalsServiceTests {

	@ParameterizedTest
	@CsvSource({ "190.87.195.8,true", "8.8.8.8,true", "2001:4860:4860::8888,true", "127.0.0.1,false", "0.0.0.0,false",
			"10.1.2.3,false", "172.16.0.9,false", "192.168.1.20,false", "169.254.1.1,false", "100.64.0.1,false",
			"100.127.255.254,false", "100.128.0.1,true", "224.0.0.1,false", "::1,false", "fe80::1,false",
			"fd12:3456::1,false", "fc00::1,false" })
	void classifiesAddresses(String address, boolean isPublic) throws Exception {
		assertThat(SignalsService.isPublic(InetAddress.getByName(address))).isEqualTo(isPublic);
	}

	@Test
	void aCityAndACountryTogetherAreAnAvailableLocation() {
		assertThat(SignalsService.reportedLocation(body("San Salvador", "SV", null))).isEqualTo(LocationStatus.AVAILABLE);
		assertThat(SignalsService.reportedLocation(body("San Salvador", "SV", LocationStatus.AVAILABLE)))
			.isEqualTo(LocationStatus.AVAILABLE);
	}

	@Test
	void sayingItIsUnavailableIsNotTheSameAsSayingNothing() {
		assertThat(SignalsService.reportedLocation(body(null, null, LocationStatus.UNAVAILABLE)))
			.isEqualTo(LocationStatus.UNAVAILABLE);
		assertThat(SignalsService.reportedLocation(body(null, null, null))).isNull();
		assertThat(SignalsService.reportedLocation(body("  ", null, null))).isNull();
	}

	@Test
	void theCityAndTheCountryGoTogetherAndNeverWithUnavailable() {
		assertThatThrownBy(() -> SignalsService.reportedLocation(body("San Salvador", null, null)))
			.isInstanceOf(InvalidInputException.class);
		assertThatThrownBy(() -> SignalsService.reportedLocation(body(null, "SV", null)))
			.isInstanceOf(InvalidInputException.class);
		assertThatThrownBy(() -> SignalsService.reportedLocation(body("San Salvador", "SV", LocationStatus.UNAVAILABLE)))
			.isInstanceOf(InvalidInputException.class);
		assertThatThrownBy(() -> SignalsService.reportedLocation(body(null, null, LocationStatus.AVAILABLE)))
			.isInstanceOf(InvalidInputException.class);
	}

	private static CaptureSignalsRequest body(String city, String countryIso, LocationStatus status) {
		return new CaptureSignalsRequest("fp", null, null, null, city, countryIso, status, null, null);
	}

}
