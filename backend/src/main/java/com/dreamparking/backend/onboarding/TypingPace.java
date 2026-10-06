package com.dreamparking.backend.onboarding;

import com.dreamparking.backend.common.DbEnum;
import com.dreamparking.backend.common.DbEnumConverter;

import jakarta.persistence.Converter;

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
