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
import com.dreamparking.backend.identity.entity.EncryptedImage;
import com.dreamparking.backend.identity.entity.IdentityDocument;
import com.dreamparking.backend.identity.entity.enums.DuiDateProblem;
import com.dreamparking.backend.identity.entity.enums.OcrStatus;
import com.dreamparking.backend.identity.ocr.DuiReading;
import com.dreamparking.backend.liveness.entity.LivenessCheck;
import com.dreamparking.backend.liveness.entity.LivenessStatus;
import com.dreamparking.backend.onboarding.entity.RequestSignals;
import com.dreamparking.backend.onboarding.entity.enums.TypingPace;
import com.dreamparking.backend.risk.entity.ScoreModel;
import com.dreamparking.backend.risk.entity.enums.RiskLevel;
import com.dreamparking.backend.risk.entity.enums.ScoreSignal;
import com.dreamparking.backend.rnpn.entity.RnpnCheck;
import com.dreamparking.backend.rnpn.entity.enums.RnpnField;
import com.dreamparking.backend.rnpn.entity.enums.RnpnOutcome;
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
		assertThat(contribution(result, ScoreSignal.CITY).observation()).isEqualTo("Aún no se evalúa en el score");
		assertThat(contribution(result, ScoreSignal.INTERNAL_LISTS).points()).isNull();
		assertThat(contribution(result, ScoreSignal.INTERNAL_LISTS).maxPoints()).isNull();
		assertThat(contribution(result, ScoreSignal.INTERNAL_LISTS).observation()).startsWith("Aún no disponible");
		// The RNPN is read when the request is submitted: before that there is no check and the model gives it no weight
		assertThat(contribution(result, ScoreSignal.RNPN).points()).isNull();
		assertThat(contribution(result, ScoreSignal.RNPN).observation()).isEqualTo("Aún no se ha cotejado con el RNPN");
		assertThat(result.contributions()).hasSize(ScoreSignal.values().length);
	}

	@Test
	void anRnpnThatMatchesAddsNothingButCountsAsRead() {
		Contribution rnpn = contribution(ScoreEngine.score(modelWithRnpn(), withRnpn(rnpn(RnpnOutcome.MATCH, 1))),
				ScoreSignal.RNPN);

		assertThat(rnpn.points()).isZero();
		assertThat(rnpn.maxPoints()).isEqualTo(15);
		assertThat(rnpn.observation()).isEqualTo("Cotejo RNPN (simulado): los datos coinciden");
	}

	@Test
	void anRnpnThatDoesNotAnswerAddsHalfThePoints() {
		Contribution rnpn = contribution(ScoreEngine.score(modelWithRnpn(), withRnpn(rnpn(RnpnOutcome.UNAVAILABLE, 3))),
				ScoreSignal.RNPN);

		assertThat(rnpn.points()).isEqualTo(8);
		assertThat(rnpn.observation()).isEqualTo("Cotejo RNPN (simulado): el RNPN no respondió después de 3 intentos");
	}

	@Test
	void anRnpnThatDoesNotMatchAddsAllThePointsAndSaysWhichFields() {
		Result result = ScoreEngine.score(modelWithRnpn(), withRnpn(rnpn(RnpnOutcome.NO_MATCH, 1)));

		assertThat(contribution(result, ScoreSignal.RNPN).points()).isEqualTo(15);
		assertThat(contribution(result, ScoreSignal.RNPN).observation())
			.isEqualTo("Cotejo RNPN (simulado): los datos no coinciden (apellidos, fecha de nacimiento)");
		assertThat(result.score()).isEqualTo(15);
	}

	@Test
	void anRnpnThatDoesNotAnswerAfterOneTryUsesTheSingular() {
		Contribution rnpn = contribution(ScoreEngine.score(modelWithRnpn(), withRnpn(rnpn(RnpnOutcome.UNAVAILABLE, 1))),
				ScoreSignal.RNPN);

		assertThat(rnpn.observation()).isEqualTo("Cotejo RNPN (simulado): el RNPN no respondió después de 1 intento");
	}

	@Test
	void everyDataOfTheDuiThatDoesNotMatchIsNamedInSpanish() {
		List<RnpnField> all = List.of(RnpnField.DUI, RnpnField.FIRST_NAMES, RnpnField.LAST_NAMES, RnpnField.BIRTH_DATE);
		RnpnCheck check = new RnpnCheck(null, RnpnOutcome.NO_MATCH, "simulated", 1, all, null);

		assertThat(contribution(ScoreEngine.score(modelWithRnpn(), withRnpn(check)), ScoreSignal.RNPN).observation())
			.isEqualTo("Cotejo RNPN (simulado): los datos no coinciden (número de DUI, nombres, apellidos, fecha de nacimiento)");
	}

	@Test
	void aModelWithoutAWeightForTheRnpnIgnoresTheCheck() {
		Result result = ScoreEngine.score(model, withRnpn(rnpn(RnpnOutcome.NO_MATCH, 1)));

		assertThat(contribution(result, ScoreSignal.RNPN).points()).isNull();
		assertThat(contribution(result, ScoreSignal.RNPN).maxPoints()).isNull();
		assertThat(result.score()).isZero();
	}

	@Test
	void theRnpnCheckOfARealProviderIsNotLabelledAsSimulated() {
		RnpnCheck real = new RnpnCheck(null, RnpnOutcome.MATCH, "rnpn-sv", 1, List.of(), null);

		assertThat(contribution(ScoreEngine.score(modelWithRnpn(), withRnpn(real)), ScoreSignal.RNPN).observation())
			.isEqualTo("Cotejo RNPN: los datos coinciden");
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
		IdentityDocument otherNames = confirmed(OcrStatus.READ, reading(VALID_DUI, true, "0.95"), VALID_DUI, "Ana",
				"Pérez");
		otherNames.compareWithDeclared(VALID_DUI, "Otra", "Persona");

		assertThat(points(mismatch)).isEqualTo(6);
		// Without a face match (B-13) a different name weighs more than a different DUI number
		assertThat(points(otherNames)).isEqualTo(12);
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

	@ParameterizedTest
	@CsvSource({ "0.90, 1", "0.50, 5", "0.20, 8", "0.00, 10" })
	void theLivenessConfidenceIsReadAsOneMinusItsSeverity(String confidence, int points) {
		Result result = ScoreEngine.score(modelWithLiveness(), withLiveness(liveness(LivenessStatus.CHECKED, confidence, 1)));

		assertThat(contribution(result, ScoreSignal.LIVENESS).points()).isEqualTo(points);
		assertThat(contribution(result, ScoreSignal.LIVENESS).maxPoints()).isEqualTo(10);
		assertThat(contribution(result, ScoreSignal.LIVENESS).observation()).startsWith("Confianza de la prueba de vida:");
	}

	@Test
	void theLivenessObservationSaysTheConfidenceAsAPercentage() {
		Result result = ScoreEngine.score(modelWithLiveness(), withLiveness(liveness(LivenessStatus.CHECKED, "0.87", 1)));

		assertThat(contribution(result, ScoreSignal.LIVENESS).observation()).isEqualTo("Confianza de la prueba de vida: 87 %");
	}

	@Test
	void takingTheSelfieThreeTimesHasAFloorEvenWithHighConfidence() {
		Result result = ScoreEngine.score(modelWithLiveness(), withLiveness(liveness(LivenessStatus.CHECKED, "0.95", 3)));

		assertThat(contribution(result, ScoreSignal.LIVENESS).points()).isEqualTo(5);
		assertThat(contribution(result, ScoreSignal.LIVENESS).observation()).endsWith("selfie tomada 3 veces");
		// two tries are not repeated yet
		Result twice = ScoreEngine.score(modelWithLiveness(), withLiveness(liveness(LivenessStatus.CHECKED, "0.95", 2)));
		assertThat(contribution(twice, ScoreSignal.LIVENESS).points()).isEqualTo(1);
	}

	@Test
	void theFloorDoesNotLowerAnAlreadyHighSeverity() {
		Result result = ScoreEngine.score(modelWithLiveness(), withLiveness(liveness(LivenessStatus.CHECKED, "0.10", 4)));

		assertThat(contribution(result, ScoreSignal.LIVENESS).points()).isEqualTo(9);
	}

	@Test
	void withoutLivenessOrWithAProviderFailureTheSignalAddsNothing() {
		for (LivenessCheck check : new LivenessCheck[] { null, liveness(LivenessStatus.FAILED, null, 1) }) {
			Result result = ScoreEngine.score(modelWithLiveness(), withLiveness(check));

			assertThat(contribution(result, ScoreSignal.LIVENESS).points()).isNull();
			assertThat(contribution(result, ScoreSignal.LIVENESS).maxPoints()).isEqualTo(10);
			assertThat(result.score()).isZero();
		}
		assertThat(contribution(ScoreEngine.score(modelWithLiveness(), withLiveness(null)), ScoreSignal.LIVENESS)
			.observation()).isEqualTo("Sin prueba de vida en el expediente");
		assertThat(contribution(ScoreEngine.score(modelWithLiveness(),
				withLiveness(liveness(LivenessStatus.FAILED, null, 1))), ScoreSignal.LIVENESS).observation())
			.isEqualTo("El proveedor de prueba de vida no estuvo disponible");
	}

	@Test
	void livenessAtItsWorstOnlyChangesTheScoreAndTheContributionsAddUpToIt() {
		ScoreModel withLiveness = modelWithLiveness();
		Result result = ScoreEngine.score(withLiveness, withLiveness(liveness(LivenessStatus.CHECKED, "0.00", 1)));

		assertThat(result.score()).isEqualTo(10);
		assertThat(withLiveness.levelOf(result.score())).isEqualTo(RiskLevel.LOW);
		assertThat(result.contributions().stream().mapToInt(c -> c.points() == null ? 0 : c.points()).sum())
			.isEqualTo(result.score());
	}

	@Test
	void aModelWithoutAWeightForLivenessIgnoresTheCheck() {
		Result result = ScoreEngine.score(model, withLiveness(liveness(LivenessStatus.CHECKED, "0.00", 1)));

		assertThat(contribution(result, ScoreSignal.LIVENESS).points()).isNull();
		assertThat(contribution(result, ScoreSignal.LIVENESS).maxPoints()).isNull();
		assertThat(result.score()).isZero();
	}

	private int points(IdentityDocument document) {
		return contribution(ScoreEngine.score(model, inputs(null, document, null)), ScoreSignal.IDENTITY_DOCUMENT)
			.points();
	}

	private static Contribution contribution(Result result, ScoreSignal signal) {
		return result.contributions().stream().filter(c -> c.signal() == signal).findFirst().orElseThrow();
	}

	private static Inputs inputs(MonthlyAmountRange range, IdentityDocument document, RequestSignals signals) {
		return new Inputs(range, document, signals, null, null, TODAY);
	}

	private static Inputs withLiveness(LivenessCheck liveness) {
		return new Inputs(null, null, null, liveness, null, TODAY);
	}

	private static Inputs withRnpn(RnpnCheck rnpn) {
		return new Inputs(null, null, null, null, rnpn, TODAY);
	}

	/** A check of the simulated RNPN; a result that does not match names the last names and the birth date. */
	private static RnpnCheck rnpn(RnpnOutcome outcome, int tries) {
		List<RnpnField> fields = outcome == RnpnOutcome.NO_MATCH ? List.of(RnpnField.LAST_NAMES, RnpnField.BIRTH_DATE)
				: List.of();
		return new RnpnCheck(null, outcome, "simulated", tries, fields,
				outcome == RnpnOutcome.UNAVAILABLE ? "does not answer" : null);
	}

	private static ScoreModel modelWithRnpn() {
		return modelWith(Map.of(ScoreSignal.RNPN, 15), 39, 69);
	}

	/** A liveness check after the given tries; the last one answered with this status and confidence. */
	private static LivenessCheck liveness(LivenessStatus status, String confidence, int tries) {
		LivenessCheck check = new LivenessCheck(null);
		for (int i = 0; i < tries; i++) {
			check.record(status, "simulated", confidence == null ? null : new BigDecimal(confidence),
					status == LivenessStatus.FAILED ? "provider off" : null,
					new EncryptedImage("image/jpeg", new byte[12], new byte[] { 1 }, (short) 1, "a".repeat(64), 1),
					Instant.now());
		}
		return check;
	}

	/** Model v1 with a weight for the liveness check, as the next model will have. */
	private static ScoreModel modelWithLiveness() {
		return modelWith(Map.of(ScoreSignal.AMOUNT, 25, ScoreSignal.IDENTITY_DOCUMENT, 20, ScoreSignal.TYPING_PACE, 15,
				ScoreSignal.DEVICE, 10, ScoreSignal.TIME_OF_DAY, 10, ScoreSignal.CITY, 20, ScoreSignal.LIVENESS, 10), 39,
				69);
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
