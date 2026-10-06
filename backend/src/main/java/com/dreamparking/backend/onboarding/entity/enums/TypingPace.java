package com.dreamparking.backend.onboarding.entity.enums;

import jakarta.persistence.Converter;

import com.dreamparking.backend.common.persistence.DbEnum;
import com.dreamparking.backend.common.persistence.DbEnumConverter;

/** Typing pace category captured during an onboarding session. Mapped to the PostgreSQL enum {@code ritmo_escritura}. */
public enum TypingPace implements DbEnum {

	SLOW("PAUSADO"),
	NORMAL("NORMAL"),
	FAST("RAPIDO");

	private final String dbValue;

	TypingPace(String dbValue) {
		this.dbValue = dbValue;
	}

	@Override
	public String dbValue() {
		return dbValue;
	}

	@Converter
	public static class JpaConverter extends DbEnumConverter<TypingPace> {

		public JpaConverter() {
			super(TypingPace.class);
		}

	}

}
