package com.dreamparking.backend.onboarding;

import com.dreamparking.backend.common.PgEnumJdbcType;
import com.dreamparking.backend.customer.Customer;
import com.dreamparking.backend.customer.Device;
import com.dreamparking.backend.risk.RiskLevel;

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
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Onboarding request (aggregate root). Personal data is a snapshot of what the customer declared. */
@Entity
@Table(name = "solicitud")
public class OnboardingRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id")
	private UUID id;

	@Column(name = "numero", unique = true, length = 14)
	private String number;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cliente_id")
	private Customer customer;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "dispositivo_id")
	private Device device;

	@Convert(converter = RequestStatus.JpaConverter.class)
	@JdbcType(PgEnumJdbcType.class)
	@Column(name = "estado", nullable = false, columnDefinition = "estado_solicitud")
	private RequestStatus status = RequestStatus.IN_PROGRESS;

	@Column(name = "etapas_completadas", nullable = false)
	private Short completedSteps = (short) 0;

	@Convert(converter = RiskLevel.JpaConverter.class)
	@JdbcType(PgEnumJdbcType.class)
	@Column(name = "nivel_riesgo", nullable = false, columnDefinition = "nivel_riesgo")
	private RiskLevel riskLevel = RiskLevel.NOT_EVALUATED;

	@Column(name = "nombres", length = 100)
	private String firstNames;

	@Column(name = "apellidos", length = 100)
	private String lastNames;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "dui", length = 10)
	private String dui;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "celular", length = 9)
	private String mobilePhone;

	@Column(name = "canal", nullable = false, length = 20)
	private String channel = "APP_MOVIL";

	@CreationTimestamp
	@Column(name = "iniciada_en", nullable = false)
	private Instant startedAt;

	@Column(name = "enviada_en")
	private Instant submittedAt;

	@Column(name = "ultima_actividad_en", nullable = false)
	private Instant lastActivityAt = Instant.now();

	@Column(name = "idempotency_key", unique = true)
	private UUID idempotencyKey;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

	public UUID getId() {
		return id;
	}

	public String getNumber() {
		return number;
	}

	public void setNumber(String number) {
		this.number = number;
	}

	public Customer getCustomer() {
		return customer;
	}

	public void setCustomer(Customer customer) {
		this.customer = customer;
	}

	public Device getDevice() {
		return device;
	}

	public void setDevice(Device device) {
		this.device = device;
	}

	public RequestStatus getStatus() {
		return status;
	}

	public void setStatus(RequestStatus status) {
		this.status = status;
	}

	public Short getCompletedSteps() {
		return completedSteps;
	}

	public void setCompletedSteps(Short completedSteps) {
		this.completedSteps = completedSteps;
	}

	public RiskLevel getRiskLevel() {
		return riskLevel;
	}

	public void setRiskLevel(RiskLevel riskLevel) {
		this.riskLevel = riskLevel;
	}

	public String getFirstNames() {
		return firstNames;
	}

	public void setFirstNames(String firstNames) {
		this.firstNames = firstNames;
	}

	public String getLastNames() {
		return lastNames;
	}

	public void setLastNames(String lastNames) {
		this.lastNames = lastNames;
	}

	public String getDui() {
		return dui;
	}

	public void setDui(String dui) {
		this.dui = dui;
	}

	public String getMobilePhone() {
		return mobilePhone;
	}

	public void setMobilePhone(String mobilePhone) {
		this.mobilePhone = mobilePhone;
	}

	public String getChannel() {
		return channel;
	}

	public void setChannel(String channel) {
		this.channel = channel;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getSubmittedAt() {
		return submittedAt;
	}

	public void setSubmittedAt(Instant submittedAt) {
		this.submittedAt = submittedAt;
	}

	public Instant getLastActivityAt() {
		return lastActivityAt;
	}

	public void setLastActivityAt(Instant lastActivityAt) {
		this.lastActivityAt = lastActivityAt;
	}

	public UUID getIdempotencyKey() {
		return idempotencyKey;
	}

	public void setIdempotencyKey(UUID idempotencyKey) {
		this.idempotencyKey = idempotencyKey;
	}

	public Integer getVersion() {
		return version;
	}

}
