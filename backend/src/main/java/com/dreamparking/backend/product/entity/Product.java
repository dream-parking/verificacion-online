package com.dreamparking.backend.product.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

/** An account the bank offers (VDI-146), with its main conditions. Switching {@code active} off stops offering it. */
@Entity
@Table(name = "product")
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Short id;

	@Column(name = "code", nullable = false, length = 30, updatable = false)
	private String code;

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Column(name = "description", nullable = false, length = 250)
	private String description;

	@Column(name = "monthly_fee_usd", nullable = false)
	private BigDecimal monthlyFeeUsd;

	@Column(name = "minimum_opening_deposit_usd", nullable = false)
	private BigDecimal minimumOpeningDepositUsd;

	@Column(name = "annual_interest_rate", nullable = false)
	private BigDecimal annualInterestRate;

	@Column(name = "active", nullable = false)
	private boolean active;

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

	public String getDescription() {
		return description;
	}

	public BigDecimal getMonthlyFeeUsd() {
		return monthlyFeeUsd;
	}

	public BigDecimal getMinimumOpeningDepositUsd() {
		return minimumOpeningDepositUsd;
	}

	public BigDecimal getAnnualInterestRate() {
		return annualInterestRate;
	}

	public boolean isActive() {
		return active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
