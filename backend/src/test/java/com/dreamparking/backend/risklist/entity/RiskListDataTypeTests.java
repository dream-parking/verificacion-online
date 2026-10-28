package com.dreamparking.backend.risklist.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.dreamparking.backend.risklist.entity.enums.RiskListDataType;

/** The normalization has to give the same text for what an administrator types and for what a request carries. */
class RiskListDataTypeTests {

	@ParameterizedTest
	@ValueSource(strings = { "04567891-2", "045678912", " 04567891-2 ", "0456 7891 2" })
	void aDuiIsItsNineDigitsWhateverTheWayItIsWritten(String raw) {
		assertThat(RiskListDataType.DOCUMENT.normalize(raw)).contains("045678912");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "  ", "0456789", "0456789123", "abcdefghi" })
	void somethingThatIsNotADuiHasNoNormalForm(String raw) {
		assertThat(RiskListDataType.DOCUMENT.normalize(raw)).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "7845-2310", "78452310", "+503 7845-2310", "503 78452310", "(+503)78452310" })
	void aMobilePhoneIsItsEightDigitsWithoutHyphenOrCountryCode(String raw) {
		assertThat(RiskListDataType.PHONE.normalize(raw)).contains("78452310");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "2245-2310", "7845-231", "7845-23100", "+504 7845-2310", "no es un teléfono" })
	void somethingThatIsNotAMobilePhoneHasNoNormalForm(String raw) {
		assertThat(RiskListDataType.PHONE.normalize(raw)).isEmpty();
	}

	@Test
	void aDeviceFingerprintKeepsItsCaseAndOnlyLosesTheSpacesAtTheEnds() {
		assertThat(RiskListDataType.DEVICE.normalize("  d4f1·9A3c·e7b2 ")).contains("d4f1·9A3c·e7b2");
		assertThat(RiskListDataType.DEVICE.normalize("d4f1·9a3c·e7b2")).isNotEqualTo(RiskListDataType.DEVICE.normalize("D4F1·9A3C·E7B2"));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   ", "0123456789012345678901234567890123456789012345678901234567890123456789" })
	void aBlankOrTooLongFingerprintHasNoNormalForm(String raw) {
		assertThat(RiskListDataType.DEVICE.normalize(raw)).isEmpty();
	}

}
