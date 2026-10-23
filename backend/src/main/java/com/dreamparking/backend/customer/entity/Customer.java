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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/** Customer identified by their DUI. */
@Entity
@Table(name = "customer")
public class Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id")
	private UUID id;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "dui", nullable = false, unique = true, length = 10)
	private String dui;

	@Column(name = "first_names", nullable = false, length = 100)
	private String firstNames;

	@Column(name = "last_names", nullable = false, length = 100)
	private String lastNames;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "mobile_phone", nullable = false, length = 9)
	private String mobilePhone;

	/**
	 * Customer number such as CLI-0000001: assigned once, when the first request is submitted, and never changed. It is
	 * written only by {@code CustomerRepository.assignNumber} and the entity never updates it ({@code updatable = false}),
	 * so correcting the customer's data cannot overwrite a number another submission assigned in the meantime.
	 */
	@Column(name = "number", length = 20, insertable = false, updatable = false)
	private String number;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public UUID getId() {
		return id;
	}

	public String getDui() {
		return dui;
	}

	public void setDui(String dui) {
		this.dui = dui;
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

	public String getMobilePhone() {
		return mobilePhone;
	}

	public void setMobilePhone(String mobilePhone) {
		this.mobilePhone = mobilePhone;
	}

	/** The customer number, or null until the customer submits the first request. */
	public String getNumber() {
		return number;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
