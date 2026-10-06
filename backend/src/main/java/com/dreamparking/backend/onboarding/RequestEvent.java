package com.dreamparking.backend.onboarding;

import com.dreamparking.backend.common.PgEnumJdbcType;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only timeline entry of an onboarding request. */
@Entity
@Table(name = "evento_solicitud")
public class RequestEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "solicitud_id", nullable = false)
	private OnboardingRequest request;

	@Convert(converter = RequestEventType.JpaConverter.class)
	@JdbcType(PgEnumJdbcType.class)
	@Column(name = "tipo", nullable = false, columnDefinition = "tipo_evento_sol")
	private RequestEventType type;

	@Column(name = "descripcion", nullable = false, length = 250)
	private String description;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "datos", nullable = false)
	private Map<String, Object> data = new HashMap<>();

	@CreationTimestamp
	@Column(name = "ocurrido_en", nullable = false)
	private Instant occurredAt;

	@Column(name = "actor", nullable = false, length = 60)
	private String actor = "CLIENTE";

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
