package com.dreamparking.backend.outcome.entity;

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

import com.dreamparking.backend.outcome.entity.enums.OutcomeEntryLink;
import com.dreamparking.backend.risklist.entity.RiskListEntry;

/** Which risk list entry an outcome created or took off. Insert-only, like the outcome. */
@Entity
@Table(name = "request_outcome_entry")
public class RequestOutcomeEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "outcome_id", nullable = false, updatable = false)
	private RequestOutcome outcome;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "entry_id", nullable = false, updatable = false)
	private RiskListEntry entry;

	@Enumerated(EnumType.STRING)
	@Column(name = "link", nullable = false, length = 8, updatable = false)
	private OutcomeEntryLink link;

	protected RequestOutcomeEntry() {
	}

	public RequestOutcomeEntry(RequestOutcome outcome, RiskListEntry entry, OutcomeEntryLink link) {
		this.outcome = outcome;
		this.entry = entry;
		this.link = link;
	}

	public RequestOutcome getOutcome() {
		return outcome;
	}

	public RiskListEntry getEntry() {
		return entry;
	}

	public OutcomeEntryLink getLink() {
		return link;
	}

}
