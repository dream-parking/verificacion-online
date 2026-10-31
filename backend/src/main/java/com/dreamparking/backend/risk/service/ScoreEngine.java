package com.dreamparking.backend.risk.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.dreamparking.backend.catalog.entity.MonthlyAmountRange;
import com.dreamparking.backend.identity.entity.IdentityDocument;
import com.dreamparking.backend.identity.entity.enums.DuiDateProblem;
import com.dreamparking.backend.identity.entity.enums.OcrStatus;
import com.dreamparking.backend.identity.service.DuiValidator;
import com.dreamparking.backend.liveness.entity.LivenessCheck;
import com.dreamparking.backend.liveness.entity.LivenessStatus;
import com.dreamparking.backend.onboarding.entity.RequestSignals;
import com.dreamparking.backend.onboarding.entity.enums.TypingPace;
import com.dreamparking.backend.risk.entity.ScoreModel;
import com.dreamparking.backend.risk.entity.enums.ScoreSignal;
import com.dreamparking.backend.rnpn.entity.RnpnCheck;
import com.dreamparking.backend.rnpn.entity.enums.RnpnField;

/**
 * Scores a request with a {@link ScoreModel}. Each signal is read as a severity from 0 (nothing unusual) to 1 (as
 * unusual as it gets) and adds {@code severity x weight} points, rounded; so changing a weight in the model changes how
 * much the signal counts without touching the rules below. A signal that is not available adds nothing: it neither
 * raises nor lowers the score, and no signal approves or rejects a request by itself.
 */
public final class ScoreEngine {

	/** Signals with no way to be read yet; they are listed in the breakdown as not available. */
	private static final List<ScoreSignal> PENDING = List.of(ScoreSignal.CITY, ScoreSignal.INTERNAL_LISTS);

	private static final BigDecimal LOW_CONFIDENCE = new BigDecimal("0.70");

	/** From this many captures of the DUI photos, each one more adds to the score. */
	private static final int REPEATED_CAPTURES = 3;

	/** From this many tries of the liveness check, the signal cannot be lower than {@link #REPEATED_TRIES_SEVERITY}. */
	private static final int REPEATED_LIVENESS_TRIES = 3;

	private static final double REPEATED_TRIES_SEVERITY = 0.5;

	/** An RNPN that did not answer is neither good nor bad news: half of the points of the signal. */
	private static final double RNPN_UNAVAILABLE_SEVERITY = 0.5;

	/** Highest ceiling (USD) of the declared monthly range for each severity step: up to 200, 500 and 1,000. */
	private static final int[] AMOUNT_CEILINGS = { 200, 500, 1000 };

	private static final double[] AMOUNT_SEVERITIES = { 0.0, 0.16, 0.32, 0.8 };

	private ScoreEngine() {
	}

	/** What one signal added: {@code points} null when it was not available, {@code maxPoints} null without weight. */
	public record Contribution(ScoreSignal signal, Integer points, Integer maxPoints, String observation) {
	}

	/** The score, between 0 and 100, and what each signal added to it. */
	public record Result(int score, List<Contribution> contributions) {
	}

	/** Everything the signals are read from; any part can be null while it has not been captured. */
	public record Inputs(MonthlyAmountRange amountRange, IdentityDocument document, RequestSignals signals,
			LivenessCheck liveness, RnpnCheck rnpn, LocalDate today) {
	}

	public static Result score(ScoreModel model, Inputs inputs) {
		List<Contribution> contributions = new ArrayList<>();
		contributions.add(amount(model, inputs.amountRange()));
		contributions.add(identityDocument(model, inputs.document(), inputs.today()));
		contributions.add(typingPace(model, inputs.signals()));
		contributions.add(device(model, inputs.signals()));
		contributions.add(timeOfDay(model, inputs.signals()));
		contributions.add(liveness(model, inputs.liveness()));
		contributions.add(rnpn(model, inputs.rnpn()));
		for (ScoreSignal signal : PENDING) {
			String note = signal == ScoreSignal.CITY ? "Aún no se evalúa en el score"
					: "Aún no disponible: llega en otro sprint";
			contributions.add(new Contribution(signal, null, model.weightOf(signal), note));
		}
		int total = contributions.stream().mapToInt(c -> c.points() == null ? 0 : c.points()).sum();
		return new Result(Math.min(total, 100), List.copyOf(contributions));
	}

	private static Contribution amount(ScoreModel model, MonthlyAmountRange range) {
		if (range == null) {
			return unavailable(model, ScoreSignal.AMOUNT, "Todavía no hay un monto declarado");
		}
		BigDecimal ceiling = range.getMaxUsd();
		int step = 0;
		while (step < AMOUNT_CEILINGS.length && (ceiling == null || ceiling.compareTo(BigDecimal.valueOf(AMOUNT_CEILINGS[step])) > 0)) {
			step++;
		}
		return contribution(model, ScoreSignal.AMOUNT, AMOUNT_SEVERITIES[step],
				"Monto mensual declarado: %s".formatted(range.getLabel()));
	}

	private static Contribution identityDocument(ScoreModel model, IdentityDocument document, LocalDate today) {
		if (document == null || !document.isConfirmed()) {
			return unavailable(model, ScoreSignal.IDENTITY_DOCUMENT, "Sin DUI confirmado en el expediente");
		}
		List<String> findings = new ArrayList<>();
		double severity = 0;
		if (Boolean.FALSE.equals(document.getLooksAuthentic())) {
			severity += 0.6;
			findings.add("el documento no parece auténtico");
		}
		else if (document.isEverLookedInauthentic()) {
			severity += 0.4;
			findings.add("una captura anterior no parecía auténtica");
		}
		if (!DuiValidator.hasValidCheckDigit(document.getConfirmedDui())) {
			severity += 0.4;
			findings.add("el dígito verificador del DUI no es válido");
		}
		if (document.isExpired(today)) {
			severity += 0.3;
			findings.add("el DUI está vencido");
		}
		if (Boolean.FALSE.equals(document.getDuiMatchesDeclared())) {
			severity += 0.3;
			findings.add("el DUI no coincide con el de los datos básicos");
		}
		// Weighs more than a different DUI number: without a face match (B-13) a different name is the only sign that the
		// DUI may belong to someone else.
		if (Boolean.FALSE.equals(document.getNamesMatchDeclared())) {
			severity += 0.6;
			findings.add("los nombres no coinciden con los de los datos básicos");
		}
		if (document.getStatus() == OcrStatus.FAILED) {
			severity += 0.25;
			findings.add("el lector no estuvo disponible y la persona escribió los datos");
		}
		else if (document.getConfidence() != null && document.getConfidence().compareTo(LOW_CONFIDENCE) < 0) {
			severity += 0.1;
			findings.add("el lector tuvo poca confianza en la lectura");
		}
		int corrected = Math.min(document.getCorrectedFields().size(), 4);
		if (corrected > 0) {
			severity += 0.05 * corrected;
			findings.add(corrected == 1 ? "1 dato corregido" : corrected + " datos corregidos");
		}
		int rejected = document.getRejectedConfirmations();
		if (rejected > 0) {
			severity += Math.min(0.2 + 0.1 * (rejected - 1), 0.4);
			String reasons = String.join(", ", document.getRejectionReasons().stream().map(DuiDateProblem::label).toList());
			findings.add((rejected == 1 ? "1 confirmación rechazada" : rejected + " confirmaciones rechazadas")
					+ (reasons.isEmpty() ? "" : " (" + reasons + ")"));
		}
		int extraCaptures = Math.min(document.getAttempts() - REPEATED_CAPTURES + 1, 4);
		if (extraCaptures > 0) {
			severity += 0.05 * extraCaptures;
			findings.add("fotos del DUI capturadas %d veces".formatted(document.getAttempts()));
		}
		String observation = findings.isEmpty() ? "DUI legible; los datos coinciden con lo escrito"
				: "DUI confirmado: " + String.join("; ", findings);
		return contribution(model, ScoreSignal.IDENTITY_DOCUMENT, severity, observation);
	}

	private static Contribution typingPace(ScoreModel model, RequestSignals signals) {
		TypingPace pace = signals == null ? null : signals.getTypingPace();
		if (pace == null) {
			return unavailable(model, ScoreSignal.TYPING_PACE, "Sin telemetría de escritura");
		}
		String cpm = signals.getTypingSpeedCpm() == null ? "" : ", unos %d caracteres por minuto".formatted(signals.getTypingSpeedCpm());
		return switch (pace) {
			case SLOW -> contribution(model, ScoreSignal.TYPING_PACE, 0.4, "Lento" + cpm);
			case NORMAL -> contribution(model, ScoreSignal.TYPING_PACE, 0.1, "Normal" + cpm);
			case FAST -> contribution(model, ScoreSignal.TYPING_PACE, 0.8, "Muy rápido" + cpm);
		};
	}

	private static Contribution device(ScoreModel model, RequestSignals signals) {
		Long requests = signals == null || signals.getDeviceFingerprint() == null ? null
				: signals.getRequestsFromSameDevice();
		if (requests == null) {
			return unavailable(model, ScoreSignal.DEVICE, "Sin huella del dispositivo");
		}
		if (requests <= 1) {
			return contribution(model, ScoreSignal.DEVICE, 0.2, "Primera solicitud desde este teléfono");
		}
		return contribution(model, ScoreSignal.DEVICE, requests == 2 ? 0.6 : 1.0,
				"%d solicitudes desde este teléfono".formatted(requests));
	}

	private static Contribution timeOfDay(ScoreModel model, RequestSignals signals) {
		Boolean night = signals == null ? null : signals.getNightTime();
		if (night == null) {
			return unavailable(model, ScoreSignal.TIME_OF_DAY, "Sin horario de la solicitud");
		}
		return night ? contribution(model, ScoreSignal.TIME_OF_DAY, 1.0, "Nocturno: entre las 9 p. m. y las 5 a. m.")
				: contribution(model, ScoreSignal.TIME_OF_DAY, 0.0, "Diurno");
	}

	/**
	 * The provider's confidence read as a severity: 1 minus the confidence, and at least {@code 0.5} from the third try,
	 * since repeating the selfie until it passes is itself unusual. A provider that did not answer adds nothing.
	 */
	private static Contribution liveness(ScoreModel model, LivenessCheck check) {
		if (check == null) {
			return unavailable(model, ScoreSignal.LIVENESS, "Sin prueba de vida en el expediente");
		}
		if (check.getStatus() == LivenessStatus.FAILED) {
			return unavailable(model, ScoreSignal.LIVENESS, "El proveedor de prueba de vida no estuvo disponible");
		}
		double severity = BigDecimal.ONE.subtract(check.getConfidence()).doubleValue();
		String observation = "Confianza de la prueba de vida: %d %%".formatted(
				check.getConfidence().movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValue());
		if (check.getAttempts() >= REPEATED_LIVENESS_TRIES) {
			severity = Math.max(severity, REPEATED_TRIES_SEVERITY);
			observation += "; selfie tomada %d veces".formatted(check.getAttempts());
		}
		return contribution(model, ScoreSignal.LIVENESS, severity, observation);
	}

	/**
	 * The last RNPN check read as a severity (VDI-111): no points when the data matches, all of them when it does not,
	 * and half when the RNPN did not answer. It adds to the score without deciding anything by itself.
	 */
	private static Contribution rnpn(ScoreModel model, RnpnCheck check) {
		if (check == null) {
			return unavailable(model, ScoreSignal.RNPN, "Aún no se ha cotejado con el RNPN");
		}
		String source = check.isSimulated() ? "Cotejo RNPN (simulado)" : "Cotejo RNPN";
		return switch (check.getOutcome()) {
			case MATCH -> contribution(model, ScoreSignal.RNPN, 0.0, source + ": los datos coinciden");
			case NO_MATCH -> contribution(model, ScoreSignal.RNPN, 1.0, source + ": los datos no coinciden ("
					+ String.join(", ", check.getMismatchedFields().stream().map(ScoreEngine::fieldName).toList()) + ")");
			case UNAVAILABLE -> contribution(model, ScoreSignal.RNPN, RNPN_UNAVAILABLE_SEVERITY,
					source + ": el RNPN no respondió después de %d %s".formatted(check.getAttempts(),
							check.getAttempts() == 1 ? "intento" : "intentos"));
		};
	}

	/** The data of the DUI as the person reading the observation knows it. */
	private static String fieldName(RnpnField field) {
		return switch (field) {
			case DUI -> "número de DUI";
			case FIRST_NAMES -> "nombres";
			case LAST_NAMES -> "apellidos";
			case BIRTH_DATE -> "fecha de nacimiento";
		};
	}

	private static Contribution contribution(ScoreModel model, ScoreSignal signal, double severity, String observation) {
		Integer weight = model.weightOf(signal);
		if (weight == null) {
			return new Contribution(signal, null, null, observation);
		}
		int points = (int) Math.round(Math.min(severity, 1.0) * weight);
		return new Contribution(signal, points, weight, observation);
	}

	private static Contribution unavailable(ScoreModel model, ScoreSignal signal, String observation) {
		return new Contribution(signal, null, model.weightOf(signal), observation);
	}

}
