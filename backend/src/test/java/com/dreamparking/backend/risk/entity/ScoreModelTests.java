package com.dreamparking.backend.risk.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.dreamparking.backend.risk.entity.enums.RiskLevel;

/** VDI-117: the threshold of the enhanced review is where the high range of the model begins. */
class ScoreModelTests {

	private final ScoreModel model = modelWith(39, 69);

	@Test
	void theThresholdIsTheTopOfTheMediumRange() {
		assertThat(model.reviewThreshold()).isEqualTo((short) 69);
	}

	@Test
	void onlyAScoreAboveTheThresholdRequiresEnhancedReview() {
		assertThat(model.requiresEnhancedReview(0)).isFalse();
		assertThat(model.requiresEnhancedReview(69)).isFalse();
		assertThat(model.requiresEnhancedReview(70)).isTrue();
		assertThat(model.requiresEnhancedReview(100)).isTrue();
	}

	@Test
	void theEnhancedReviewAndTheHighLevelAgreeOnEveryScore() {
		for (int score = 0; score <= 100; score++) {
			assertThat(model.requiresEnhancedReview(score)).as("score %d", score)
				.isEqualTo(model.levelOf(score) == RiskLevel.HIGH);
		}
	}

	private static ScoreModel modelWith(int lowMax, int mediumMax) {
		ScoreModel model = new ScoreModel();
		ReflectionTestUtils.setField(model, "version", (short) 1);
		ReflectionTestUtils.setField(model, "lowMax", (short) lowMax);
		ReflectionTestUtils.setField(model, "mediumMax", (short) mediumMax);
		return model;
	}

}
