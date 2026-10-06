package com.dreamparking.backend.onboarding.entity.enums;

import jakarta.persistence.Converter;

import com.dreamparking.backend.common.persistence.DbEnum;
import com.dreamparking.backend.common.persistence.DbEnumConverter;

/** Lifecycle status of an onboarding request. Mapped to the PostgreSQL enum {@code estado_solicitud}. */
public enum RequestStatus implements DbEnum {

	IN_PROGRESS("EN_PROGRESO"),
	COMPLETED("COMPLETADA"),
	ABANDONED("ABANDONADA");

	private final String dbValue;

	RequestStatus(String dbValue) {
		this.dbValue = dbValue;
	}

	@Override
	public String dbValue() {
		return dbValue;
	}

	@Converter
	public static class JpaConverter extends DbEnumConverter<RequestStatus> {

		public JpaConverter() {
			super(RequestStatus.class);
		}

	}

}
