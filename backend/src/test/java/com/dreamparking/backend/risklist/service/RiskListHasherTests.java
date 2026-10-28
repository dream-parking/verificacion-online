package com.dreamparking.backend.risklist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.dreamparking.backend.risklist.entity.enums.RiskListDataType;

class RiskListHasherTests {

	static final String SALT = "una-sal-secreta-de-prueba";

	RiskListHasher hasher = new RiskListHasher(SALT, new MockEnvironment());

	@Test
	void theSameValueAlwaysGivesTheSameHashOf64LowercaseHexCharacters() {
		String hash = hasher.hash(RiskListDataType.DOCUMENT, "045678912");

		assertThat(hash).matches("[0-9a-f]{64}").isEqualTo(hasher.hash(RiskListDataType.DOCUMENT, "045678912"));
		assertThat(hash).doesNotContain("045678912");
	}

	@Test
	void theTypeIsPartOfTheHashSoTheSameDigitsOfOtherTypesDoNotCollide() {
		assertThat(hasher.hash(RiskListDataType.DOCUMENT, "78452310")).isNotEqualTo(hasher.hash(RiskListDataType.PHONE, "78452310"))
			.isNotEqualTo(hasher.hash(RiskListDataType.DEVICE, "78452310"));
	}

	@Test
	void anotherSaltGivesAnotherHash() {
		RiskListHasher other = new RiskListHasher("otra-sal-secreta-de-prueba", new MockEnvironment());

		assertThat(other.hash(RiskListDataType.DOCUMENT, "045678912"))
			.isNotEqualTo(hasher.hash(RiskListDataType.DOCUMENT, "045678912"));
	}

	@Test
	void aSaltThatIsTooShortIsRefused() {
		assertThatThrownBy(() -> new RiskListHasher("corta", new MockEnvironment()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("at least 16");
	}

	@Test
	void withoutASaltOnlyTheDevProfileStarts() {
		assertThatThrownBy(() -> new RiskListHasher(" ", new MockEnvironment()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("APP_RISK_LISTS_SALT");

		MockEnvironment dev = new MockEnvironment();
		dev.setActiveProfiles("dev");
		RiskListHasher local = new RiskListHasher("", dev);
		assertThat(local.hash(RiskListDataType.DOCUMENT, "045678912"))
			.isEqualTo(new RiskListHasher(RiskListHasher.LOCAL_SALT, new MockEnvironment()).hash(RiskListDataType.DOCUMENT,
					"045678912"));
	}

}
