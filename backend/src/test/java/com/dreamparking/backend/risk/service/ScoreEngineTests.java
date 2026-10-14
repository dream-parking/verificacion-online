package com.dreamparking.backend.risk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.dreamparking.backend.catalog.entity.MonthlyAmountRange;
import com.dreamparking.backend.identity.entity.IdentityDocument;
import com.dreamparking.backend.identity.entity.enums.DuiDateProblem;
import com.dreamparking.backend.identity.entity.enums.OcrStatus;
import com.dreamparking.backend.identity.ocr.DuiReading;
import com.dreamparking.backend.onboarding.entity.RequestSignals;
import com.dreamparking.backend.onboarding.entity.enums.TypingPace;
import com.dreamparking.backend.risk.entity.ScoreModel;
import com.dreamparking.backend.risk.entity.enums.RiskLevel;
import com.dreamparking.backend.risk.entity.enums.ScoreSignal;
import com.dreamparking.backend.risk.service.ScoreEngine.Contribution;
import com.dreamparking.backend.risk.service.ScoreEngine.Inputs;
import com.dreamparking.backend.risk.service.ScoreEngine.Result;

/** The weights of model v1 applied to each signal, and how the points add up to a score and a level. */
class ScoreEngineTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

	private static final String VALID_DUI = "03377841-2";

	private final ScoreModel model = modelV1();

	@ParameterizedTest
	@CsvSource({ "200, 0", "500, 4", "1000, 8", "5000, 20" })
	void theAmountAddsMorePointsAsTheRangeCeilingGrows(int ceiling, int points) {
		Result result = ScoreEngine.score(model, inputs(range(BigDecimal.valueOf(ceiling)), null, null));

		assertThat(contribution(result, ScoreSignal.AMOUNT).points()).isEqualTo(points);
		assertThat(contribution(result, ScoreSignal.AMOUNT).maxPoints()).isEqualTo(25);
	}

	@Test
	void anOpenEndedRangeCountsAsTheHighestOne() {
		Result result = ScoreEngine.score(model, inputs(range(null), null, null));

		assertThat(contribution(result, ScoreSignal.AMOUNT).points()).isEqualTo(20);
	}

	@Test
	void signalsNotCapturedAddNothingAndAreListedAsNotAvailable() {
		Result result = ScoreEngine.score(model, inputs(range(BigDecimal.valueOf(500)), null, null));

		assertThat(result.score()).isEqualTo(4);
		assertThat(contribution(result, ScoreSignal.IDENTITY_DOCUMENT).points()).isNull();
		assertThat(contribution(result, ScoreSignal.IDENTITY_DOCUMENT).maxPoints()).isEqualTo(20);
		assertThat(contribution(result, ScoreSignal.TYPING_PACE).points()).isNull();
		assertThat(contribution(result, ScoreSignal.DEVICE).points()).isNull();
		assertThat(contribution(result, ScoreSignal.TIME_OF_DAY).points()).isNull();
		// Not available yet: the city (weight, but nothing to read it from) and the signals with no weight
		assertThat(contribution(result, ScoreSignal.CITY).points()).isNull();
		assertThat(contribution(result, ScoreSignal.CITY).maxPoints()).isEqualTo(20);
		for (ScoreSignal pending : new ScoreSignal[] { ScoreSignal.RNPN, ScoreSignal.LIVENESS,
				ScoreSignal.INTERNAL_LISTS }) {
			assertThat(contribution(result, pending).points()).isNull();
			assertThat(contribution(result, pending).maxPoints()).isNull();
			assertThat(contribution(result, pending).observation()).startsWith("Aún no disponible");
		}
		assertThat(result.contributions()).hasSize(ScoreSignal.values().length);
	}

	@ParameterizedTest
	@CsvSource({ "SLOW, 6", "NORMAL, 2", "FAST, 12" })
	void theTypingPaceWeighsByHowUnusualItIs(TypingPace pace, int points) {
		RequestSignals signals = signals(pace, 190, "fp", 1L, false);

		Result result = ScoreEngine.score(model, inputs(null, null, signals));

		assertThat(contribution(result, ScoreSignal.TYPING_PACE).points()).isEqualTo(points);
		assertThat(contribution(result, ScoreSignal.TYPING_PACE).observation()).endsWith("190 caracteres por minuto");
	}

	@ParameterizedTest
	@CsvSource({ "1, 2", "2, 6", "3, 10", "9, 10" })
	void theMoreRequestsFromTheSameDeviceTheMorePoints(long requests, int points) {
		Result result = ScoreEngine.score(model, inputs(null, null, signals(null, null, "fp", requests, null)));

		assertThat(contribution(result, ScoreSignal.DEVICE).points()).isEqualTo(points);
	}

	@Test
	void withoutADeviceFingerprintTheDeviceIsNotAvailable() {
		Result result = ScoreEngine.score(model, inputs(null, null, signals(null, null, null, 1L, null)));

		assertThat(contribution(result, ScoreSignal.DEVICE).points()).isNull();
	}

	@Test
	void nightTimeAddsTheWholeWeightAndDaytimeNothing() {
		Result night = ScoreEngine.score(model, inputs(null, null, signals(null, null, null, null, true)));
		Result day = ScoreEngine.score(model, inputs(null, null, signals(null, null, null, null, false)));

		assertThat(contribution(night, ScoreSignal.TIME_OF_DAY).points()).isEqualTo(10);
		assertThat(contribution(day, ScoreSignal.TIME_OF_DAY).points()).isZero();
		assertThat(contribution(day, ScoreSignal.TIME_OF_DAY).observation()).isEqualTo("Diurno");
	}

	@Test
	void aCleanDocumentAddsNothing() {
		IdentityDocument document = confirmed(OcrStatus.READ, reading(VALID_DUI, true, "0.95"), VALID_DUI, "Ana", "Pérez");
		document.compareWithDeclared(VALID_DUI, "Ana", "Pérez");

		Contribution contribution = contribution(ScoreEngine.score(model, inputs(null, document, null)),
				ScoreSignal.IDENTITY_DOCUMENT);

		assertThat(contribution.points()).isZero();
		assertThat(contribution.observation()).isEqualTo("DUI legible; los datos coinciden con lo escrito");
	}

	@Test
	void everyProblemWithTheDocumentAddsPointsAndTheyAreCapped() {
		// Invalid check digit, expired, does not look authentic and the names are not the declared ones
		IdentityDocument document = confirmed(OcrStatus.READ, reading("03377841-3", false, "0.95"), "03377841-3", "Ana",
				"Pérez");
		document.compareWithDeclared("03377841-3", "Otra", "Persona");
		ReflectionTestUtils.setField(document, "confirmedExpiryDate", LocalDate.of(2020, 1, 1));

		Contribution contribution = contribution(ScoreEngine.score(model, inputs(null, document, null)),
				ScoreSignal.IDENTITY_DOCUMENT);

		assertThat(contribution.points()).isEqualTo(20);
		assertThat(contribution.observation()).contains("no parece auténtico", "dígito verificador", "vencido",
				"nombres no coinciden");
	}

	@Test
	void documentChecksAddTheirOwnPoints() {
		IdentityDocument mismatch = confirmed(OcrStatus.READ, reading(VALID_DUI, true, "0.95"), VALID_DUI, "Ana", "Pérez");
		mismatch.compareWithDeclared("00000000-0", "Ana", "Pérez");
		IdentityDocument lowConfidence = confirmed(OcrStatus.READ, reading(VALID_DUI, true, "0.40"), VALID_DUI, "Ana",
				"Pérez");
		IdentityDocument corrected = confirmed(OcrStatus.READ, reading(VALID_DUI, true, "0.95"), VALID_DUI, "Ana María",
				"Pérez");
		IdentityDocument typed = confirmed(OcrStatus.FAILED, null, VALID_DUI, "Ana", "Pérez");

		assertThat(points(mismatch)).isEqualTo(6);
		assertThat(points(lowConfidence)).isEqualTo(2);
		assertThat(points(corrected)).isEqualTo(1);
		assertThat(points(typed)).isEqualTo(5);
	}

	@Test
	void rejectedConfirmationsAddPointsAndNameTheirReasons() {
		IdentityDocument once = confirmed(OcrStatus.READ, reading(VALID_DUI, true, "0.95"), VALID_DUI, "Ana", "Pérez");
		once.recordRejectedConfirmation(List.of(DuiDateProblem.EXPIRED));
		IdentityDocument twice = confirmed(OcrStatus.READ, reading(VALID_DUI, true, "0.95"), VALID_DUI, "Ana", "Pérez");
		twice.recordRejectedConfirmation(List.of(DuiDateProblem.EXPIRED));
		twice.recordRejectedConfirmation(List.of(DuiDateProblem.UNDER_AGE, DuiDateProblem.EXPIRED));
		IdentityDocument many = confirmed(OcrStatus.READ, reading(VALID_DUI, true, "0.95"), VALID_DUI, "Ana", "Pérez");
		for (int i = 0; i < 6; i++) {
			many.recordRejectedConfirmation(List.of(DuiDateProblem.EXPIRED));
		}

		assertThat(points(once)).isEqualTo(4);
		assertThat(points(twice)).isEqualTo(6);
		assertThat(points(many)).isEqualTo(8);
		assertThat(contribution(ScoreEngine.score(model, inputs(null, twice, null)), ScoreSignal.IDENTITY_DOCUMENT)
			.observation()).contains("2 confirmaciones rechazadas (DUI vencido, menor de 18 años)");
	}

	@Test
	void aCaptureThatDidNotLookAuthenticCountsEvenIfALaterOneDid() {
		IdentityDocument document = new IdentityDocument(null);
		document.recordReading(OcrStatus.READ, reading(VALID_DUI, false, "0.95"), null, "model", Instant.now());
		document.recordReading(OcrStatus.READ, reading(VALID_DUI, true, "0.95"), null, "model", Instant.now());
		document.confirm(VALID_DUI, "Ana", "Pérez", LocalDate.of(1990, 1, 1), LocalDate.of(2031, 1, 1), Instant.now());
		document.compareWithDeclared(VALID_DUI, "Ana", "Pérez");

		Contribution contribution = contribution(ScoreEngine.score(model, inputs(null, document, null)),
				ScoreSignal.IDENTITY_DOCUMENT);

		assertThat(contribution.points()).isEqualTo(8);
		assertThat(contribution.observation()).contains("una captura anterior no parecía auténtica")
			.doesNotContain("el documento no parece auténtico");
	}

	@ParameterizedTest
	@CsvSource({ "1, 0", "2, 0", "3, 1", "5, 3", "6, 4", "10, 4" })
	void fromTheThirdCaptureEachOneAddsAPoint(int captures, int points) {
		IdentityDocument document = new IdentityDocument(null);
		for (int i = 0; i < captures; i++) {
			document.recordReading(OcrStatus.READ, reading(VALID_DUI, true, "0.95"), null, "model", Instant.now());
		}
		document.confirm(VALID_DUI, "Ana", "Pérez", LocalDate.of(1990, 1, 1), LocalDate.of(2031, 1, 1), Instant.now());
		document.compareWithDeclared(VALID_DUI, "Ana", "Pérez");

		assertThat(points(document)).isEqualTo(points);
	}

	@Test
	void aDocumentTheCustomerHasNotConfirmedIsNotAvailable() {
		IdentityDocument document = new IdentityDocument(null);
		document.recordReading(OcrStatus.READ, reading(VALID_DUI, true, "0.95"), null, "model", Instant.now());

		Contribution contribution = contribution(ScoreEngine.score(model, inputs(null, document, null)),
				ScoreSignal.IDENTITY_DOCUMENT);

		assertThat(contribution.points()).isNull();
		assertThat(contribution.observation()).isEqualTo("Sin DUI confirmado en el expediente");
	}

	@Test
	void theScoreAddsThePointsOfAllTheSignals() {
		RequestSignals signals = signals(TypingPace.NORMAL, 190, "fp", 1L, false);
		IdentityDocument document = confirmed(OcrStatus.READ, reading(VALID_DUI, true, "0.95"), VALID_DUI, "Ana María",
				"Pérez");

		Result result = ScoreEngine.score(model, inputs(range(BigDecimal.valueOf(1000)), document, signals));

		// amount 8 + document 1 (one corrected field) + typing 2 + device 2 + daytime 0
		assertThat(result.score()).isEqualTo(13);
		assertThat(result.contributions().stream().mapToInt(c -> c.points() == null ? 0 : c.points()).sum())
			.isEqualTo(13);
	}

	@Test
	void theScoreNeverGoesAbove100() {
		ScoreModel heavy = modelWith(Map.of(ScoreSignal.AMOUNT, 100, ScoreSignal.TIME_OF_DAY, 100), 39, 69);

		Result result = ScoreEngine.score(heavy, inputs(range(null), null, signals(null, null, null, null, true)));

		assertThat(result.score()).isEqualTo(100);
	}

	@Test
	void aSignalWithoutWeightInTheModelAddsNothing() {
		ScoreModel noAmount = modelWith(Map.of(ScoreSignal.TIME_OF_DAY, 10), 39, 69);

		Result result = ScoreEngine.score(noAmount, inputs(range(BigDecimal.valueOf(5000)), null, null));

		assertThat(contribution(result, ScoreSignal.AMOUNT).points()).isNull();
		assertThat(contribution(result, ScoreSignal.AMOUNT).maxPoints()).isNull();
		assertThat(result.score()).isZero();
	}

	@Test
	void aHeavierWeightMakesTheSameSignalCountMore() {
		ScoreModel heavier = modelWith(Map.of(ScoreSignal.TIME_OF_DAY, 40), 39, 69);

		Result result = ScoreEngine.score(heavier, inputs(null, null, signals(null, null, null, null, true)));

		assertThat(contribution(result, ScoreSignal.TIME_OF_DAY).points()).isEqualTo(40);
		assertThat(heavier.levelOf(result.score())).isEqualTo(RiskLevel.MEDIUM);
	}

	@ParameterizedTest
	@CsvSource({ "0, LOW", "39, LOW", "40, MEDIUM", "69, MEDIUM", "70, HIGH", "100, HIGH" })
	void theRangesOfTheModelSetTheLevel(int score, RiskLevel level) {
		assertThat(model.levelOf(score)).isEqualTo(level);
	}

	private int points(IdentityDocument document) {
		return contribution(ScoreEngine.score(model, inputs(null, document, null)), ScoreSignal.IDENTITY_DOCUMENT)
			.points();
	}

	private static Contribution contribution(Result result, ScoreSignal signal) {
		return result.contributions().stream().filter(c -> c.signal() == signal).findFirst().orElseThrow();
	}

	private static Inputs inputs(MonthlyAmountRange range, IdentityDocument document, RequestSignals signals) {
		return new Inputs(range, document, signals, TODAY);
	}

	private static MonthlyAmountRange range(BigDecimal maxUsd) {
		MonthlyAmountRange range = mock(MonthlyAmountRange.class);
		when(range.getMaxUsd()).thenReturn(maxUsd);
		when(range.getLabel()).thenReturn("USD 200.01 a 500");
		return range;
	}

	private static RequestSignals signals(TypingPace pace, Integer cpm, String fingerprint, Long requestsFromSameDevice,
			Boolean nightTime) {
		RequestSignals signals = mock(RequestSignals.class);
		when(signals.getTypingPace()).thenReturn(pace);
		when(signals.getTypingSpeedCpm()).thenReturn(cpm == null ? null : cpm.shortValue());
		when(signals.getDeviceFingerprint()).thenReturn(fingerprint);
		when(signals.getRequestsFromSameDevice()).thenReturn(requestsFromSameDevice);
		when(signals.getNightTime()).thenReturn(nightTime);
		return signals;
	}

	private static DuiReading reading(String dui, boolean looksAuthentic, String confidence) {
		return new DuiReading(true, null, null, dui, "Ana", "Pérez", LocalDate.of(1990, 1, 1), null,
				LocalDate.of(2031, 1, 1), "F", looksAuthentic, new BigDecimal(confidence));
	}

	/** A document read (or not) and confirmed with the given data; the confirmed expiry date is in the future. */
	private static IdentityDocument confirmed(OcrStatus status, DuiReading reading, String dui, String firstNames,
			String lastNames) {
		IdentityDocument document = new IdentityDocument(null);
		document.recordReading(status, reading, status == OcrStatus.FAILED ? "provider off" : null, "model",
				Instant.now());
		document.confirm(dui, firstNames, lastNames, LocalDate.of(1990, 1, 1), LocalDate.of(2031, 1, 1), Instant.now());
		document.compareWithDeclared(dui, firstNames, lastNames);
		return document;
	}

	private static ScoreModel modelV1() {
		return modelWith(Map.of(ScoreSignal.AMOUNT, 25, ScoreSignal.IDENTITY_DOCUMENT, 20, ScoreSignal.TYPING_PACE, 15,
				ScoreSignal.DEVICE, 10, ScoreSignal.TIME_OF_DAY, 10, ScoreSignal.CITY, 20), 39, 69);
	}

	private static ScoreModel modelWith(Map<ScoreSignal, Integer> weights, int lowMax, int mediumMax) {
		ScoreModel model = new ScoreModel();
		Map<ScoreSignal, Short> stored = new EnumMap<>(ScoreSignal.class);
		weights.forEach((signal, points) -> stored.put(signal, points.shortValue()));
		ReflectionTestUtils.setField(model, "version", (short) 1);
		ReflectionTestUtils.setField(model, "weights", stored);
		ReflectionTestUtils.setField(model, "lowMax", (short) lowMax);
		ReflectionTestUtils.setField(model, "mediumMax", (short) mediumMax);
		return model;
	}

}
