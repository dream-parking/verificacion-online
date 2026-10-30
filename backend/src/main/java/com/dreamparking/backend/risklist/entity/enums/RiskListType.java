package com.dreamparking.backend.risklist.entity.enums;

/** What an internal risk list says about the data on it. */
public enum RiskListType {

	CONFIRMED_FRAUD("Fraude confirmado"),
	UNDER_INVESTIGATION("En investigación"),
	WATCH("Vigilancia");

	private final String label;

	RiskListType(String label) {
		this.label = label;
	}

	/** Name shown in the console. */
	public String label() {
		return label;
	}

}
