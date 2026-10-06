package com.dreamparking.backend.customer.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

/** Device fingerprint, reused across requests to detect several requests from the same device. */
@Entity
@Table(name = "dispositivo")
public class Device {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id")
	private UUID id;

	@Column(name = "huella", nullable = false, unique = true, length = 64)
	private String fingerprint;

	@Column(name = "modelo", length = 80)
	private String model;

	@Column(name = "sistema_op", length = 30)
	private String operatingSystem;

	@CreationTimestamp
	@Column(name = "primera_vez_en", nullable = false)
	private Instant firstSeenAt;

	@Column(name = "ultima_vez_en", nullable = false)
	private Instant lastSeenAt = Instant.now();

	public UUID getId() {
		return id;
	}

	public String getFingerprint() {
		return fingerprint;
	}

	public void setFingerprint(String fingerprint) {
		this.fingerprint = fingerprint;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getOperatingSystem() {
		return operatingSystem;
	}

	public void setOperatingSystem(String operatingSystem) {
		this.operatingSystem = operatingSystem;
	}

	public Instant getFirstSeenAt() {
		return firstSeenAt;
	}

	public Instant getLastSeenAt() {
		return lastSeenAt;
	}

	public void setLastSeenAt(Instant lastSeenAt) {
		this.lastSeenAt = lastSeenAt;
	}

}
