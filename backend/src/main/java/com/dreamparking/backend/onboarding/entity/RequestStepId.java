package com.dreamparking.backend.onboarding.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;

import org.hibernate.annotations.JdbcType;

import com.dreamparking.backend.common.persistence.PgEnumJdbcType;
import com.dreamparking.backend.onboarding.entity.enums.OnboardingStep;

/** Primary key of {@link RequestStep}: one row per request and step. */
@Embeddable
public class RequestStepId implements Serializable {

	@Column(name = "solicitud_id")
	private UUID requestId;

	@Convert(converter = OnboardingStep.JpaConverter.class)
	@JdbcType(PgEnumJdbcType.class)
	@Column(name = "paso", columnDefinition = "paso_onboarding")
	private OnboardingStep step;

	protected RequestStepId() {
	}

	public RequestStepId(UUID requestId, OnboardingStep step) {
		this.requestId = requestId;
		this.step = step;
	}

	public UUID getRequestId() {
		return requestId;
	}

	public OnboardingStep getStep() {
		return step;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof RequestStepId other && Objects.equals(requestId, other.requestId) && step == other.step;
	}

	@Override
	public int hashCode() {
		return Objects.hash(requestId, step);
	}

}
