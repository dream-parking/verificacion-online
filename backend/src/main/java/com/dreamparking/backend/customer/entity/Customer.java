package com.dreamparking.backend.customer.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Pattern;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/** Customer identified by their DUI. */
@Entity
@Table(name = "customer")
public class Customer {

	/** Letters (accents and ñ included) in words separated by single spaces; no digits or special characters. */
	public static final String NAME_PATTERN = "\\p{L}+( \\p{L}+)*";

	public static final String NAME_MESSAGE = "Solo se permiten letras y espacios";

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id")
	private UUID id;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Pattern(regexp = "[0-9]{8}-[0-9]")
	@Column(name = "dui", nullable = false, unique = true, length = 10)
	private String dui;

	@Pattern(regexp = NAME_PATTERN, message = NAME_MESSAGE)
	@Column(name = "first_names", nullable = false, length = 100)
	private String firstNames;

	@Pattern(regexp = NAME_PATTERN, message = NAME_MESSAGE)
	@Column(name = "last_names", nullable = false, length = 100)
	private String lastNames;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Pattern(regexp = "[67][0-9]{3}-[0-9]{4}")
	@Column(name = "mobile_phone", nullable = false, length = 9)
	private String mobilePhone;

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

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
