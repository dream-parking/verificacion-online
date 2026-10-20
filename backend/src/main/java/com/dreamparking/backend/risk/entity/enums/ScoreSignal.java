package com.dreamparking.backend.risk.entity.enums;

/**
 * A signal the score model combines. {@link #CITY}, {@link #RNPN} and {@link #INTERNAL_LISTS} are not available
 * yet: they show up in the breakdown as such and add nothing to the score.
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
