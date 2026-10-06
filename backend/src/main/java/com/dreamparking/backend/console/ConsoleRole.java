package com.dreamparking.backend.console;

import com.dreamparking.backend.common.DbEnum;
import com.dreamparking.backend.common.DbEnumConverter;

import jakarta.persistence.Converter;

/** Role of a KYC/AML console user. Mapped to the PostgreSQL enum {@code rol_consola}. */
public enum ConsoleRole implements DbEnum {

	KYC_LEAD("LIDER_KYC"),
	FRAUD_ANALYST("ANALISTA_FRAUDE"),
	ADMIN("ADMIN");

	private final String dbValue;

	ConsoleRole(String dbValue) {
		this.dbValue = dbValue;
	}

	@Override
	public String dbValue() {
		return dbValue;
	}

	@Converter
	public static class JpaConverter extends DbEnumConverter<ConsoleRole> {

		public JpaConverter() {
			super(ConsoleRole.class);
		}

	}

}
