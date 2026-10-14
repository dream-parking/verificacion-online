package com.dreamparking.backend.account.entity.enums;

/**
 * Ways the real activity of a month can differ from the declared profile (VDI-3), with the alert type each one raises.
 */
public enum ProfileDeviationType {

	/** The money received is above the top of the declared monthly range. */
	INFLOW_ABOVE_RANGE("PERFIL_EXCEDIDO"),

	/** The money received is above the declared estimated monthly amount. */
	INFLOW_ABOVE_DECLARED_AMOUNT("PERFIL_EXCEDIDO"),

	/** More incoming movements than declared. */
	MORE_TRANSACTIONS_THAN_DECLARED("PERFIL_EXCEDIDO"),

	/** More money left the account than declared. */
	OUTFLOW_ABOVE_DECLARED("PERFIL_EXCEDIDO"),

	/** Money received of a type the customer did not declare. */
	UNDECLARED_TRANSACTION_TYPE("ORIGEN_DISTINTO");

	private final String alertTypeCode;

	ProfileDeviationType(String alertTypeCode) {
		this.alertTypeCode = alertTypeCode;
	}

	public String alertTypeCode() {
		return alertTypeCode;
	}

}
