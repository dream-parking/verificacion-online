package com.dreamparking.backend.risklist.entity;

import java.time.Instant;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dreamparking.backend.console.entity.ConsoleUser;
import com.dreamparking.backend.risklist.entity.enums.RiskListDataType;

/**
 * A piece of data on a risk list, kept only as its salted hash. It is never deleted: the baja marks it inactive and
 * an inactive entry stops matching. The database refuses any other change.
 */
@Entity
@Table(name = "risk_list_entry")
public class RiskListEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "list_id", nullable = false, updatable = false)
	private RiskList list;

	@Enumerated(EnumType.STRING)
	@Column(name = "data_type", nullable = false, length = 10, updatable = false)
	private RiskListDataType dataType;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "value_hash", nullable = false, length = 64, updatable = false)
	private String valueHash;

	@Column(name = "reason", nullable = false, length = 250, updatable = false)
	private String reason;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "added_by", nullable = false, updatable = false)
	private ConsoleUser addedBy;

	@CreationTimestamp
	@Column(name = "added_at", nullable = false, updatable = false)
	private Instant addedAt;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "deactivated_by")
	private ConsoleUser deactivatedBy;

	@Column(name = "deactivated_at")
	private Instant deactivatedAt;

	@Column(name = "deactivation_reason", length = 250)
	private String deactivationReason;

	protected RiskListEntry() {
	}

	public RiskListEntry(RiskList list, RiskListDataType dataType, String valueHash, String reason, ConsoleUser addedBy) {
		this.list = list;
		this.dataType = dataType;
		this.valueHash = valueHash;
		this.reason = reason;
		this.addedBy = addedBy;
	}

	/** Marks the entry inactive; it stays in the file and in the change log. */
	public void deactivate(ConsoleUser by, String reason, Instant at) {
		this.active = false;
		this.deactivatedBy = by;
		this.deactivationReason = reason;
		this.deactivatedAt = at;
	}

	public Long getId() {
		return id;
	}

	public RiskList getList() {
		return list;
	}

	public RiskListDataType getDataType() {
		return dataType;
	}

	public String getReason() {
		return reason;
	}

	public ConsoleUser getAddedBy() {
		return addedBy;
	}

	public Instant getAddedAt() {
		return addedAt;
	}

	public boolean isActive() {
		return active;
	}

	public ConsoleUser getDeactivatedBy() {
		return deactivatedBy;
	}

	public Instant getDeactivatedAt() {
		return deactivatedAt;
	}

	public String getDeactivationReason() {
		return deactivationReason;
	}

}
