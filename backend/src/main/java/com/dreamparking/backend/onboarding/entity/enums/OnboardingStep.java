package com.dreamparking.backend.onboarding.entity.enums;

/** Steps of the mobile onboarding flow, in order. Same values as the PostgreSQL enum {@code onboarding_step}. */
public enum OnboardingStep {

	PRIVACY_NOTICE,
	BASIC_DATA,
	IDENTITY_DOCUMENT,
	INCOME,
	EXPECTED_ACTIVITY,
	REVIEW

}
