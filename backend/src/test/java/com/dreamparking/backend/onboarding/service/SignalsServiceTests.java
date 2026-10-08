package com.dreamparking.backend.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

}
