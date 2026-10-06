package com.dreamparking.backend.risk;

import com.dreamparking.backend.common.DbEnum;
import com.dreamparking.backend.common.DbEnumConverter;

import jakarta.persistence.Converter;

/** Risk score assigned to an onboarding request. Mapped to the PostgreSQL enum {@code nivel_riesgo}. */
public enum RiskLevel implements DbEnum {

	NOT_EVALUATED("SIN_EVALUAR"),
	LOW("BAJO"),
	PENDING_REVIEW("PENDIENTE_EVALUACION"),
	MEDIUM("MEDIO"),
	HIGH("ALTO");

	private final String dbValue;

	RiskLevel(String dbValue) {
		this.dbValue = dbValue;
	}

	@Override
	public String dbValue() {
		return dbValue;
	}

	@Converter
	public static class JpaConverter extends DbEnumConverter<RiskLevel> {

		public JpaConverter() {
			super(RiskLevel.class);
		}

	}

}
