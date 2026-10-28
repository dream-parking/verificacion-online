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

import com.dreamparking.backend.console.entity.ConsoleUser;
import com.dreamparking.backend.risklist.entity.enums.RiskListChangeAction;

/** Log of the changes to a risk list entry: who, when and why. Insert-only. */
@Entity
@Table(name = "risk_list_entry_change")
public class RiskListEntryChange {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "entry_id", nullable = false, updatable = false)
	private RiskListEntry entry;

	@Enumerated(EnumType.STRING)
	@Column(name = "action", nullable = false, length = 12, updatable = false)
	private RiskListChangeAction action;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "changed_by", nullable = false, updatable = false)
	private ConsoleUser changedBy;

	@CreationTimestamp
	@Column(name = "changed_at", nullable = false, updatable = false)
	private Instant changedAt;

	@Column(name = "reason", nullable = false, length = 250, updatable = false)
	private String reason;

	protected RiskListEntryChange() {
	}

	public RiskListEntryChange(RiskListEntry entry, RiskListChangeAction action, ConsoleUser changedBy, String reason) {
		this.entry = entry;
		this.action = action;
		this.changedBy = changedBy;
		this.reason = reason;
	}

	public Long getId() {
		return id;
	}

	public RiskListEntry getEntry() {
		return entry;
	}

	public RiskListChangeAction getAction() {
		return action;
	}

	public ConsoleUser getChangedBy() {
		return changedBy;
	}

	public Instant getChangedAt() {
		return changedAt;
	}

	public String getReason() {
		return reason;
	}

}
