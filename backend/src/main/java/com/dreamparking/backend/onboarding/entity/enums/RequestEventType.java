package com.dreamparking.backend.onboarding.entity.enums;

import jakarta.persistence.Converter;

import com.dreamparking.backend.common.persistence.DbEnum;
import com.dreamparking.backend.common.persistence.DbEnumConverter;

/** Kinds of entries in an onboarding request timeline. Mapped to the PostgreSQL enum {@code tipo_evento_sol}. */
public enum RequestEventType implements DbEnum {

	REQUEST_STARTED("SOLICITUD_INICIADA"),
	PRIVACY_ACCEPTED("PRIVACIDAD_ACEPTADA"),
	BASIC_DATA_COMPLETED("DATOS_BASICOS_COMPLETADOS"),
	INCOME_REGISTERED("INGRESOS_REGISTRADOS"),
	EXPECTED_ACTIVITY_REGISTERED("MOVIMIENTO_REGISTRADO"),
	REQUEST_SUBMITTED("SOLICITUD_ENVIADA"),
	SUBMISSION_FAILED("ENVIO_FALLIDO"),
	SCORE_ASSIGNED("SCORE_ASIGNADO"),
	REQUEST_ABANDONED("SOLICITUD_ABANDONADA");

	private final String dbValue;

	RequestEventType(String dbValue) {
		this.dbValue = dbValue;
	}

	@Override
	public String dbValue() {
		return dbValue;
	}

	@Converter
	public static class JpaConverter extends DbEnumConverter<RequestEventType> {

		public JpaConverter() {
			super(RequestEventType.class);
		}

	}

}
