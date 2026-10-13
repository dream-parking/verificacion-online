package com.dreamparking.backend.account.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
import org.hibernate.annotations.Immutable;

import com.dreamparking.backend.account.entity.enums.MovementDirection;
import com.dreamparking.backend.catalog.entity.TransactionType;

/**
 * A movement of an account as the core banking system reports it: the real activity that is compared with the declared
 * profile (VDI-3). It is never changed or deleted.
 */
@Entity
@Immutable
@Table(name = "account_movement")
public class AccountMovement {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id")
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "account_id", nullable = false)
	private Account account;

	/** Id of the movement in the core; a retry with the same id is rejected. */
	@Column(name = "external_id", nullable = false, length = 64)
	private String externalId;

	@Enumerated(EnumType.STRING)
	@Column(name = "direction", nullable = false, length = 3)
	private MovementDirection direction;

	@Column(name = "amount_usd", nullable = false, precision = 14, scale = 2)
	private BigDecimal amountUsd;

	/** Type of money as the core classifies it; null when it is not classified. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "transaction_type_code")
	private TransactionType transactionType;

	@Column(name = "description", length = 150)
	private String description;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@CreationTimestamp
	@Column(name = "registered_at", nullable = false)
	private Instant registeredAt;

	public UUID getId() {
		return id;
	}

	public Account getAccount() {
		return account;
	}

	public void setAccount(Account account) {
		this.account = account;
	}

	public String getExternalId() {
		return externalId;
	}

	public void setExternalId(String externalId) {
		this.externalId = externalId;
	}

	public MovementDirection getDirection() {
		return direction;
	}

	public void setDirection(MovementDirection direction) {
		this.direction = direction;
	}

	public BigDecimal getAmountUsd() {
		return amountUsd;
	}

	public void setAmountUsd(BigDecimal amountUsd) {
		this.amountUsd = amountUsd;
	}

	public TransactionType getTransactionType() {
		return transactionType;
	}

	public void setTransactionType(TransactionType transactionType) {
		this.transactionType = transactionType;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public void setOccurredAt(Instant occurredAt) {
		this.occurredAt = occurredAt;
	}

	public Instant getRegisteredAt() {
		return registeredAt;
	}

}
