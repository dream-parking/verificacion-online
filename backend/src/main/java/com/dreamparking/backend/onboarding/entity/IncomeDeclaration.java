package com.dreamparking.backend.onboarding.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.dreamparking.backend.catalog.entity.IncomeRange;
import com.dreamparking.backend.catalog.entity.IncomeSource;

/** Declared income (step 2), one per request. */
@Entity
@Table(name = "income_declaration")
public class IncomeDeclaration {

	@Id
	@Column(name = "request_id")
	private UUID requestId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id")
	private OnboardingRequest request;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "source_code", nullable = false)
	private IncomeSource source;

	@Column(name = "source_detail", length = 150)
	private String sourceDetail;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "range_code", nullable = false)
	private IncomeRange range;

	@CreationTimestamp
	@Column(name = "registered_at", nullable = false)
	private Instant registeredAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public UUID getRequestId() {
		return requestId;
	}

	public OnboardingRequest getRequest() {
		return request;
	}

	public void setRequest(OnboardingRequest request) {
		this.request = request;
	}

	public IncomeSource getSource() {
		return source;
	}

	public void setSource(IncomeSource source) {
		this.source = source;
	}

	public String getSourceDetail() {
		return sourceDetail;
	}

	public void setSourceDetail(String sourceDetail) {
		this.sourceDetail = sourceDetail;
	}

	public IncomeRange getRange() {
		return range;
	}

	public void setRange(IncomeRange range) {
		this.range = range;
	}

	public Instant getRegisteredAt() {
		return registeredAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
