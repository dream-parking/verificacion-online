package com.dreamparking.backend.risk.entity.enums;

/**
 * A signal the score model combines. {@link #RNPN}, {@link #LIVENESS} and {@link #INTERNAL_LISTS} are not available
 * yet: they show up in the breakdown as such and add nothing to the score. {@link #CITY} adds nothing either while the
 * phone has not reported a location, or reported that it has none.
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
