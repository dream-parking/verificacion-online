package com.dreamparking.backend.risklist.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import com.dreamparking.backend.risklist.entity.enums.RiskListType;

/** An internal risk list (for example confirmed fraud) with the weight its matches carry for the score. */
@Entity
@Table(name = "risk_list")
public class RiskList {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Short id;

	@Column(name = "code", nullable = false, length = 30, updatable = false)
	private String code;

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "list_type", nullable = false, length = 30)
	private RiskListType type;

	@Column(name = "weight", nullable = false)
	private Short weight;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public Short getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public RiskListType getType() {
		return type;
	}

	public Short getWeight() {
		return weight;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
