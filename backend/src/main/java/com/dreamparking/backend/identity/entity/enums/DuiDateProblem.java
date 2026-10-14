package com.dreamparking.backend.identity.entity.enums;

/**
 * Why the dates of a DUI confirmation were rejected: the field the customer has to fix, the message for the app,
 * and the label of the finding the console shows in the score.
 */
public enum DuiDateProblem {

	UNDER_AGE("birthDate", "La fecha de nacimiento no concuerda con un DUI: se emite a partir de los 18 años",
			"menor de 18 años"),
	EXPIRY_BEFORE_BIRTH("expiryDate", "La fecha de vencimiento debe ser posterior a la de nacimiento",
			"vencimiento anterior al nacimiento"),
	EXPIRED("expiryDate", "El DUI está vencido", "DUI vencido");

	private final String field;

	private final String message;

	private final String label;

	DuiDateProblem(String field, String message, String label) {
		this.field = field;
		this.message = message;
		this.label = label;
	}

	public String field() {
		return field;
	}

	public String message() {
		return message;
	}

	public String label() {
		return label;
	}

}
