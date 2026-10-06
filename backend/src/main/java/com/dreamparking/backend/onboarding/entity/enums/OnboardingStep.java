package com.dreamparking.backend.onboarding.entity.enums;

import jakarta.persistence.Converter;

import com.dreamparking.backend.common.persistence.DbEnum;
import com.dreamparking.backend.common.persistence.DbEnumConverter;

/** Steps of the mobile onboarding flow, in order. Mapped to the PostgreSQL enum {@code paso_onboarding}. */
public enum OnboardingStep implements DbEnum {

	PRIVACY_NOTICE("AVISO_PRIVACIDAD"),
	BASIC_DATA("DATOS_BASICOS"),
	INCOME("INGRESOS"),
	EXPECTED_ACTIVITY("MOVIMIENTO_ESPERADO"),
	REVIEW("REVISION");

	private final String dbValue;

	OnboardingStep(String dbValue) {
		this.dbValue = dbValue;
	}

	@Override
	public String dbValue() {
		return dbValue;
	}

	@Converter
	public static class JpaConverter extends DbEnumConverter<OnboardingStep> {

		public JpaConverter() {
			super(OnboardingStep.class);
		}

	}

}
