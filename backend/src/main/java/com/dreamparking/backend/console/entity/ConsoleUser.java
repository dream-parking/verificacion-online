package com.dreamparking.backend.console.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;

import com.dreamparking.backend.common.persistence.PgEnumJdbcType;
import com.dreamparking.backend.console.entity.enums.ConsoleRole;

/** User of the KYC/AML console. */
@Entity
@Table(name = "usuario_consola")
public class ConsoleUser {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id")
	private UUID id;

	@Column(name = "email", nullable = false, unique = true, length = 150)
	private String email;

	@Column(name = "nombre", nullable = false, length = 150)
	private String fullName;

	@Column(name = "cargo", nullable = false, length = 120)
	private String jobTitle;

	@Column(name = "iniciales", nullable = false, length = 3)
	private String initials;

	@Convert(converter = ConsoleRole.JpaConverter.class)
	@JdbcType(PgEnumJdbcType.class)
	@Column(name = "rol", nullable = false, columnDefinition = "rol_consola")
	private ConsoleRole role;

	@Column(name = "activo", nullable = false)
	private Boolean active = true;

	@CreationTimestamp
	@Column(name = "creado_en", nullable = false)
	private Instant createdAt;

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	public String getJobTitle() {
		return jobTitle;
	}

	public void setJobTitle(String jobTitle) {
		this.jobTitle = jobTitle;
	}

	public String getInitials() {
		return initials;
	}

	public void setInitials(String initials) {
		this.initials = initials;
	}

	public ConsoleRole getRole() {
		return role;
	}

	public void setRole(ConsoleRole role) {
		this.role = role;
	}

	public Boolean getActive() {
		return active;
	}

	public void setActive(Boolean active) {
		this.active = active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
