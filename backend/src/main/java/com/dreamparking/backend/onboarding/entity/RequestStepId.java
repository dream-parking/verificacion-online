package com.dreamparking.backend.onboarding.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dreamparking.backend.onboarding.entity.enums.OnboardingStep;

/** Primary key of {@link RequestStep}: one row per request and step. */
@Embeddable
public class RequestStepId implements Serializable {

	@Column(name = "request_id")
	private UUID requestId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "step", columnDefinition = "onboarding_step")
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
