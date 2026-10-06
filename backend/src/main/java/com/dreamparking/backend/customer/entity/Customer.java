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
@Table(name = "cliente")
public class Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id")
	private UUID id;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "dui", nullable = false, unique = true, length = 10)
	private String dui;

	@Column(name = "nombres", nullable = false, length = 100)
	private String firstNames;

	@Column(name = "apellidos", nullable = false, length = 100)
	private String lastNames;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "celular", nullable = false, length = 9)
	private String mobilePhone;

	@CreationTimestamp
	@Column(name = "creado_en", nullable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "actualizado_en", nullable = false)
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
