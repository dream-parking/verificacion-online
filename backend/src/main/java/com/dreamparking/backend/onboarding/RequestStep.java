package com.dreamparking.backend.onboarding;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/** Time spent on one step of a request; feeds "time per step" and "total duration" in the console. */
@Entity
@Table(name = "paso_solicitud")
public class RequestStep {

	@EmbeddedId
	private RequestStepId id;

	@MapsId("requestId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "solicitud_id")
	private OnboardingRequest request;

	@Column(name = "iniciado_en", nullable = false)
	private Instant startedAt;

	@Column(name = "completado_en")
	private Instant completedAt;

	/** Computed by the database from the start and completion times. */
	@Generated(event = { EventType.INSERT, EventType.UPDATE })
	@Column(name = "duracion_seg", insertable = false, updatable = false)
	private Integer durationSeconds;

	@Column(name = "intentos", nullable = false)
	private Short attempts = (short) 1;

	protected RequestStep() {
	}

	public RequestStep(OnboardingRequest request, OnboardingStep step, Instant startedAt) {
		this.id = new RequestStepId(request.getId(), step);
		this.request = request;
		this.startedAt = startedAt;
	}

	public RequestStepId getId() {
		return id;
	}

	public OnboardingRequest getRequest() {
		return request;
	}

	public OnboardingStep getStep() {
		return id.getStep();
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(Instant startedAt) {
		this.startedAt = startedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(Instant completedAt) {
		this.completedAt = completedAt;
	}

	public Integer getDurationSeconds() {
		return durationSeconds;
	}

	public Short getAttempts() {
		return attempts;
	}

	public void setAttempts(Short attempts) {
		this.attempts = attempts;
	}

}
