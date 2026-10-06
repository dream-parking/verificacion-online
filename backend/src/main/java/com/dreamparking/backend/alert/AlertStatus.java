package com.dreamparking.backend.alert;

import com.dreamparking.backend.common.DbEnum;
import com.dreamparking.backend.common.DbEnumConverter;

import jakarta.persistence.Converter;

/** Workflow status of an alert in the console inbox. Mapped to the PostgreSQL enum {@code estado_alerta}. */
public enum AlertStatus implements DbEnum {

	UNASSIGNED("SIN_ASIGNAR"),
	ASSIGNED("ASIGNADA"),
	IN_REVIEW("EN_REVISION"),
	CLOSED("CERRADA");

	private final String dbValue;

	AlertStatus(String dbValue) {
		this.dbValue = dbValue;
	}

	@Override
	public String dbValue() {
		return dbValue;
	}

	@Converter
	public static class JpaConverter extends DbEnumConverter<AlertStatus> {

		public JpaConverter() {
			super(AlertStatus.class);
		}

	}

}
