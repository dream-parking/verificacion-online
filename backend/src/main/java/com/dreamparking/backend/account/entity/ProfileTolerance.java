package com.dreamparking.backend.account.entity;

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
import org.hibernate.annotations.Immutable;

import com.dreamparking.backend.console.entity.ConsoleUser;

/**
 * A value of the tolerance of the declared profile tracking (VDI-137): how far above what was declared a figure may go
 * before it counts as a deviation. The one in force is the newest row; a change adds a row and never edits one, so the
 * rows are also the log of changes. {@code changedBy} is null for the first value, set by the migration.
 */
@Entity
@Immutable
@Table(name = "profile_tolerance")
public class ProfileTolerance {

	/** Allowed values, both included. */
	public static final int MIN_PERCENT = 0;

	public static final int MAX_PERCENT = 45;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tolerance_percent", nullable = false)
	private short tolerancePercent;

	@Column(name = "previous_percent")
	private Short previousPercent;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "changed_by")
	private ConsoleUser changedBy;

	@CreationTimestamp
	@Column(name = "changed_at", nullable = false)
	private Instant changedAt;

	protected ProfileTolerance() {
	}

	/** The change from {@code previous} to {@code tolerancePercent}, made by {@code changedBy}. */
	public ProfileTolerance(int tolerancePercent, ProfileTolerance previous, ConsoleUser changedBy) {
		this.tolerancePercent = (short) tolerancePercent;
		this.previousPercent = previous.tolerancePercent;
		this.changedBy = changedBy;
	}

	public Long getId() {
		return id;
	}

	public int getTolerancePercent() {
		return tolerancePercent;
	}

	public Short getPreviousPercent() {
		return previousPercent;
	}

	public ConsoleUser getChangedBy() {
		return changedBy;
	}

	public Instant getChangedAt() {
		return changedAt;
	}

}
