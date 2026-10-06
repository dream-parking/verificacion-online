package com.dreamparking.backend.account.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dreamparking.backend.account.entity.enums.AccountStatus;
import com.dreamparking.backend.common.persistence.PgEnumJdbcType;
import com.dreamparking.backend.customer.entity.Customer;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;

/** Bank account opened from an onboarding request. Only a core-banking token is stored, never the number. */
@Entity
@Table(name = "cuenta")
public class Account {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id")
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cliente_id", nullable = false)
	private Customer customer;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "solicitud_id")
	private OnboardingRequest request;

	@Column(name = "numero_token", nullable = false, unique = true, length = 64)
	private String numberToken;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "ultimos4", nullable = false, length = 4)
	private String lastFour;

	@Convert(converter = AccountStatus.JpaConverter.class)
	@JdbcType(PgEnumJdbcType.class)
	@Column(name = "estado", nullable = false, columnDefinition = "estado_cuenta")
	private AccountStatus status = AccountStatus.ACTIVE;

	@CreationTimestamp
	@Column(name = "abierta_en", nullable = false)
	private Instant openedAt;

	public UUID getId() {
		return id;
	}

	public Customer getCustomer() {
		return customer;
	}

	public void setCustomer(Customer customer) {
		this.customer = customer;
	}

	public OnboardingRequest getRequest() {
		return request;
	}

	public void setRequest(OnboardingRequest request) {
		this.request = request;
	}

	public String getNumberToken() {
		return numberToken;
	}

	public void setNumberToken(String numberToken) {
		this.numberToken = numberToken;
	}

	public String getLastFour() {
		return lastFour;
	}

	public void setLastFour(String lastFour) {
		this.lastFour = lastFour;
	}

	public AccountStatus getStatus() {
		return status;
	}

	public void setStatus(AccountStatus status) {
		this.status = status;
	}

	public Instant getOpenedAt() {
		return openedAt;
	}

}
