package com.dreamparking.backend.risk.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.dreamparking.backend.risk.dto.PublishScoreModelVersionRequest;
import com.dreamparking.backend.risk.entity.ScoreModel;
import com.dreamparking.backend.risk.entity.enums.ScoreSignal;

/** VDI-120: which thresholds can be saved, and how a version gives way to the next one. */
class ScoreModelServiceTests {

	private final ScoreModel inForce = model();

	@ParameterizedTest
	@CsvSource({ "0, 1", "0, 99", "39, 70", "50, 80", "98, 99" })
	void validThresholdsHaveNoProblems(String low, String medium) {
		assertThat(problems(low, medium)).isEmpty();
	}

	@Test
	void theLowEndMustBeBelowTheMediumEnd() {
		assertThat(problems("50", "40")).containsOnlyKeys("lowMax")
			.containsValue("El fin del nivel Bajo debe ser menor que el fin del nivel Medio.");
		assertThat(problems("40", "40")).containsOnlyKeys("lowMax");
	}

	@Test
	void theLowEndCannotBeNegative() {
		assertThat(problems("-1", "60")).containsEntry("lowMax", "El fin del nivel Bajo no puede ser menor que 0.");
	}

	@ParameterizedTest
	@CsvSource({ "10, 100", "10, 150", "10, 100000" })
	void theMediumEndMustBeBelowOneHundred(String low, String medium) {
		assertThat(problems(low, medium)).containsEntry("mediumMax", "El fin del nivel Medio debe ser menor que 100.");
	}

	@Test
	void aHugeNumberIsAnOutOfRangeValueNotAMalformedOne() {
		assertThat(problems("10", "99999999999999999999")).containsEntry("mediumMax",
				"El fin del nivel Medio debe ser menor que 100.");
		assertThat(problems("-99999999999999999999", "60")).containsEntry("lowMax",
				"El fin del nivel Bajo no puede ser menor que 0.");
	}

	@Test
	void bothEndsMustBeWholeNumbers() {
		assertThat(problems("39.5", "69")).containsOnlyKeys("lowMax")
			.containsValue("El fin del nivel Bajo debe ser un número entero.");
		assertThat(problems("39", "69.2")).containsOnlyKeys("mediumMax");
		assertThat(problems("39.5", "69.2")).containsOnlyKeys("lowMax", "mediumMax");
		// 40.0 is a whole number written with a decimal
		assertThat(problems("40.0", "70.00")).isEmpty();
	}

	@Test
	void thresholdsEqualToTheOnesInForceChangeNothing() {
		assertThat(problems("39", "69")).containsEntry("lowMax",
				"Los umbrales son los mismos de la versión vigente: no hay nada que cambiar.");
	}

	@Test
	void theNextVersionKeepsTheWeightsAndTakesTheNewRanges() {
		Instant now = Instant.parse("2026-10-20T10:00:00Z");

		ScoreModel next = inForce.nextVersion(30, 60, now, null);

		assertThat(next.getVersion()).isEqualTo((short) 3);
		assertThat(next.getName()).isEqualTo("Modelo de score v3");
		assertThat(next.getLowMax()).isEqualTo((short) 30);
		assertThat(next.getMediumMax()).isEqualTo((short) 60);
		assertThat(next.getValidFrom()).isEqualTo(now);
		assertThat(next.getValidTo()).isNull();
		assertThat(next.weightOf(ScoreSignal.AMOUNT)).isEqualTo(20);
		assertThat(next.weightOf(ScoreSignal.LIVENESS)).isEqualTo(10);
		assertThat(next.weightOf(ScoreSignal.INTERNAL_LISTS)).isNull();
	}

	@Test
	void closingAVersionDoesNotTouchItsRangesOrWeights() {
		Instant now = Instant.parse("2026-10-20T10:00:00Z");

		inForce.close(now);

		assertThat(inForce.getValidTo()).isEqualTo(now);
		assertThat(inForce.getLowMax()).isEqualTo((short) 39);
		assertThat(inForce.weightOf(ScoreSignal.AMOUNT)).isEqualTo(20);
	}

	@Test
	void changingTheNextVersionDoesNotChangeTheOneBefore() {
		ScoreModel next = inForce.nextVersion(30, 60, Instant.now(), null);

		@SuppressWarnings("unchecked")
		Map<ScoreSignal, Short> weights = (Map<ScoreSignal, Short>) ReflectionTestUtils.getField(next, "weights");
		weights.put(ScoreSignal.AMOUNT, (short) 99);

		assertThat(inForce.weightOf(ScoreSignal.AMOUNT)).isEqualTo(20);
	}

	private Map<String, String> problems(String low, String medium) {
		return ScoreModelService.problems(new PublishScoreModelVersionRequest(new BigDecimal(low), new BigDecimal(medium)),
				inForce);
	}

	/** Model v2: the weights of the published model and the ranges 0-39, 40-69 and 70-100. */
	private static ScoreModel model() {
		ScoreModel model = new ScoreModel();
		Map<ScoreSignal, Short> weights = new EnumMap<>(ScoreSignal.class);
		weights.put(ScoreSignal.AMOUNT, (short) 20);
		weights.put(ScoreSignal.LIVENESS, (short) 10);
		ReflectionTestUtils.setField(model, "version", (short) 2);
		ReflectionTestUtils.setField(model, "weights", weights);
		ReflectionTestUtils.setField(model, "lowMax", (short) 39);
		ReflectionTestUtils.setField(model, "mediumMax", (short) 69);
		return model;
	}

}
