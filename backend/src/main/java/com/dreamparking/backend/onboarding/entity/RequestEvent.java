package com.dreamparking.backend.onboarding.entity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dreamparking.backend.onboarding.entity.enums.RequestEventType;

/** Append-only timeline entry of an onboarding request. */
@Entity
@Table(name = "request_event")
public class RequestEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id", nullable = false)
	private OnboardingRequest request;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "type", nullable = false, columnDefinition = "request_event_type")
	private RequestEventType type;

	@Column(name = "description", nullable = false, length = 250)
	private String description;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "data", nullable = false)
	private Map<String, Object> data = new HashMap<>();

	@CreationTimestamp
	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(name = "actor", nullable = false, length = 60)
	private String actor = "CUSTOMER";

	public Long getId() {
		return id;
	}

	public OnboardingRequest getRequest() {
		return request;
	}

	public void setRequest(OnboardingRequest request) {
		this.request = request;
	}

	public RequestEventType getType() {
		return type;
	}

	public void setType(RequestEventType type) {
		this.type = type;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Map<String, Object> getData() {
		return data;
	}

	public void setData(Map<String, Object> data) {
		this.data = data;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public String getActor() {
		return actor;
	}

	public void setActor(String actor) {
		this.actor = actor;
	}

}
