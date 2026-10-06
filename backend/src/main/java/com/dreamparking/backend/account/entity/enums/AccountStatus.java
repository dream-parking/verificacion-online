package com.dreamparking.backend.account.entity.enums;

import jakarta.persistence.Converter;

import com.dreamparking.backend.common.persistence.DbEnum;
import com.dreamparking.backend.common.persistence.DbEnumConverter;

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
