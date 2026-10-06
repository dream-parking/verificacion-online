package com.dreamparking.backend.console.entity;

import java.net.InetAddress;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Audit trail of console access to personal data (KYC requirement). */
@Entity
@Table(name = "access_audit")
public class AccessAudit {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private ConsoleUser user;

	@Column(name = "action", nullable = false, length = 40)
	private String action;

	@Column(name = "entity_name", nullable = false, length = 40)
	private String entityName;

	@Column(name = "entity_id", nullable = false, length = 64)
	private String entityId;

	@JdbcTypeCode(SqlTypes.INET)
	@Column(name = "ip")
	private InetAddress ip;

	@CreationTimestamp
	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	public Long getId() {
		return id;
	}

	public ConsoleUser getUser() {
		return user;
	}

	public void setUser(ConsoleUser user) {
		this.user = user;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public String getEntityName() {
		return entityName;
	}

	public void setEntityName(String entityName) {
		this.entityName = entityName;
	}

	public String getEntityId() {
		return entityId;
	}

	public void setEntityId(String entityId) {
		this.entityId = entityId;
	}

	public InetAddress getIp() {
		return ip;
	}

	public void setIp(InetAddress ip) {
		this.ip = ip;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

}
