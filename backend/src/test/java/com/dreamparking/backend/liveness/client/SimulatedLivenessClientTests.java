package com.dreamparking.backend.liveness.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.dreamparking.backend.identity.ocr.DocumentPhoto;

/** VDI-114: the stand-in provider and the rules of the result. */
class SimulatedLivenessClientTests {

	static final DocumentPhoto SELFIE = new DocumentPhoto(new byte[] { (byte) 0xff, (byte) 0xd8, (byte) 0xff, 1 },
			"image/jpeg");

	@Test
	void alwaysAnswersTheConfiguredConfidence() {
		SimulatedLivenessClient client = new SimulatedLivenessClient(new BigDecimal("0.35"), "");

		assertThat(client.check(SELFIE).confidence()).isEqualByComparingTo("0.35");
		assertThat(client.check(SELFIE).confidence()).isEqualByComparingTo("0.35");
		assertThat(client.provider()).isEqualTo("simulated");
	}

	@Test
	void withAProblemConfiguredEverySelfieIsUnusableForThatReason() {
		SimulatedLivenessClient client = new SimulatedLivenessClient(new BigDecimal("0.90"), " TOO_DARK ");

		LivenessResult result = client.check(SELFIE);

		assertThat(result.isUsable()).isFalse();
		assertThat(result.problem()).isEqualTo(SelfieProblem.TOO_DARK);
		assertThat(result.confidence()).isNull();
		assertThat(new SimulatedLivenessClient(new BigDecimal("0.90"), null).check(SELFIE).isUsable()).isTrue();
	}

	@Test
	void anUnknownProblemStopsTheApplicationAtStart() {
		assertThatThrownBy(() -> new SimulatedLivenessClient(new BigDecimal("0.90"), "NO_SUCH_PROBLEM"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void aSelfieThatCannotBeJudgedHasNoConfidenceEvenIfOneIsGiven() {
		LivenessResult result = new LivenessResult(new BigDecimal("0.9"), SelfieProblem.BLURRY);

		assertThat(result.confidence()).isNull();
		assertThat(result.isUsable()).isFalse();
		assertThatThrownBy(() -> LivenessResult.unusable(null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void theResultIsRoundedAndKeptBetweenZeroAndOne() {
		assertThat(new LivenessResult(new BigDecimal("0.876")).confidence()).isEqualByComparingTo("0.88");
		assertThat(new LivenessResult(new BigDecimal("-0.2")).confidence()).isEqualByComparingTo("0.00");
		assertThat(new LivenessResult(new BigDecimal("3")).confidence()).isEqualByComparingTo("1.00");
	}

	@Test
	void theResultNeedsAConfidence() {
		assertThatThrownBy(() -> new LivenessResult(null)).isInstanceOf(IllegalArgumentException.class);
	}

}
