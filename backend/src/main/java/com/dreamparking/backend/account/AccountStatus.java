package com.dreamparking.backend.account;

import com.dreamparking.backend.common.DbEnum;
import com.dreamparking.backend.common.DbEnumConverter;

import jakarta.persistence.Converter;

/** Status of a bank account. Mapped to the PostgreSQL enum {@code estado_cuenta}. */
public enum AccountStatus implements DbEnum {

	ACTIVE("ACTIVA"),
	BLOCKED("BLOQUEADA"),
	CLOSED("CERRADA");

	private final String dbValue;

	AccountStatus(String dbValue) {
		this.dbValue = dbValue;
	}

	@Override
	public String dbValue() {
		return dbValue;
	}

	@Converter
	public static class JpaConverter extends DbEnumConverter<AccountStatus> {

		public JpaConverter() {
			super(AccountStatus.class);
		}

	}

}
