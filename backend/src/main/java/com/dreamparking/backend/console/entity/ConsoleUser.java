package com.dreamparking.backend.console.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dreamparking.backend.console.entity.enums.ConsoleRole;

/** User of the KYC/AML console. */
@Entity
@Table(name = "console_user")
public class ConsoleUser {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id")
	private UUID id;

	@Column(name = "email", nullable = false, unique = true, length = 150)
	private String email;

	@Column(name = "full_name", nullable = false, length = 150)
	private String fullName;

	@Column(name = "job_title", nullable = false, length = 120)
	private String jobTitle;

	@Column(name = "initials", nullable = false, length = 3)
	private String initials;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "role", nullable = false, columnDefinition = "console_role")
	private ConsoleRole role;

	@Column(name = "active", nullable = false)
	private Boolean active = true;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	/** BCrypt hash; null while the user has no password and therefore cannot sign in. */
	@Column(name = "password_hash", length = 100)
	private String passwordHash;

	@Column(name = "password_changed_at")
	private Instant passwordChangedAt;

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

	public String getPasswordHash() {
		return passwordHash;
	}

	public Instant getPasswordChangedAt() {
		return passwordChangedAt;
	}

	/** Stores a new password hash and stamps the change time. */
	public void changePasswordHash(String passwordHash, Instant changedAt) {
		this.passwordHash = passwordHash;
		this.passwordChangedAt = changedAt;
	}

	public void setActive(Boolean active) {
		this.active = active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
