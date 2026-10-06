package com.dreamparking.backend.risk;

import com.dreamparking.backend.common.DbEnum;
import com.dreamparking.backend.common.DbEnumConverter;

import jakarta.persistence.Converter;

/** Approval status of a score rule version. Mapped to the PostgreSQL enum {@code estado_regla}. */
public enum RuleStatus implements DbEnum {

	DRAFT("BORRADOR"),
	PROVISIONAL("PROVISIONAL"),
	CONFIRMED("CONFIRMADA"),
	RETIRED("RETIRADA");

	private final String dbValue;

	RuleStatus(String dbValue) {
		this.dbValue = dbValue;
	}

	@Override
	public String dbValue() {
		return dbValue;
	}

	@Converter
	public static class JpaConverter extends DbEnumConverter<RuleStatus> {

		public JpaConverter() {
			super(RuleStatus.class);
		}

	}

}
