package com.dreamparking.backend.alert;

import com.dreamparking.backend.common.DbEnum;
import com.dreamparking.backend.common.DbEnumConverter;

import jakarta.persistence.Converter;

/** Criticality of an alert, from most to least severe. Mapped to the PostgreSQL enum {@code criticidad_alerta}. */
public enum AlertCriticality implements DbEnum {

	CRITICAL("CRITICA"),
	HIGH("ALTA"),
	MEDIUM("MEDIA"),
	LOW("BAJA");

	private final String dbValue;

	AlertCriticality(String dbValue) {
		this.dbValue = dbValue;
	}

	@Override
	public String dbValue() {
		return dbValue;
	}

	@Converter
	public static class JpaConverter extends DbEnumConverter<AlertCriticality> {

		public JpaConverter() {
			super(AlertCriticality.class);
		}

	}

}
