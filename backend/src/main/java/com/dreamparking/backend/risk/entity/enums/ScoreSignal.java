package com.dreamparking.backend.risk.entity.enums;

/**
 * A signal the score model combines. {@link #INTERNAL_LISTS} is not available yet: it shows up in the breakdown as
 * such and adds nothing to the score.
 * <p>
 * {@link #CITY} is retired: no model from the one that removed it on weighs it and the engine no longer lists it. It
 * stays in the enum because the older models, the evaluations they made and the frozen dossiers still carry it, and
 * they have to keep reading.
 */
public enum ScoreSignal {

	AMOUNT("Monto declarado"),
	IDENTITY_DOCUMENT("Documento de identidad"),
	TYPING_PACE("Ritmo de escritura"),
	DEVICE("Dispositivo"),
	TIME_OF_DAY("Horario"),
	CITY("Ciudad"),
	RNPN("Cotejo RNPN"),
	LIVENESS("Prueba de vida"),
	INTERNAL_LISTS("Listas internas");

	private final String label;

	ScoreSignal(String label) {
		this.label = label;
	}

	/** Name shown in the console. */
	public String label() {
		return label;
	}

}
