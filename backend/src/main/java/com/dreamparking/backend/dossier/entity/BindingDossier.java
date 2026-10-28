package com.dreamparking.backend.dossier.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dreamparking.backend.onboarding.entity.OnboardingRequest;

/**
 * The binding dossier of a request (VDI-131): a frozen copy of the evidence that backed the approval, written when the
 * customer signs. {@code evidence} is the JSON of {@code BindingDossierResponse.Evidence}, kept as text so it is read
 * back exactly as it was written. The file only receives new dossiers: the database refuses to change one, or to delete it before
 * {@code retainedUntil}.
 */
@Entity
@Table(name = "binding_dossier")
public class BindingDossier {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id", nullable = false, updatable = false)
	private OnboardingRequest request;

	@Column(name = "contract_version", nullable = false, length = 30, updatable = false)
	private String contractVersion;

	@Column(name = "contract_fingerprint", nullable = false, length = 64, updatable = false)
	private String contractFingerprint;

	@Column(name = "signed_at", nullable = false, updatable = false)
	private Instant signedAt;

	/** The date until the dossier is retained (VDI-134): the signature plus the retention period; no deletion before it. */
	@Column(name = "retained_until", nullable = false, updatable = false)
	private Instant retainedUntil;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "evidence", nullable = false, updatable = false)
	private String evidence;

	@CreationTimestamp
	@Column(name = "frozen_at", nullable = false, updatable = false)
	private Instant frozenAt;

	protected BindingDossier() {
	}

	public BindingDossier(OnboardingRequest request, String contractVersion, String contractFingerprint,
			Instant signedAt, Instant retainedUntil, String evidence) {
		this.request = request;
		this.contractVersion = contractVersion;
		this.contractFingerprint = contractFingerprint;
		this.signedAt = signedAt;
		this.retainedUntil = retainedUntil;
		this.evidence = evidence;
	}

	public Long getId() {
		return id;
	}

	public OnboardingRequest getRequest() {
		return request;
	}

	public String getContractVersion() {
		return contractVersion;
	}

	public String getContractFingerprint() {
		return contractFingerprint;
	}

	public Instant getSignedAt() {
		return signedAt;
	}

	public Instant getRetainedUntil() {
		return retainedUntil;
	}

	public String getEvidence() {
		return evidence;
	}

	public Instant getFrozenAt() {
		return frozenAt;
	}

}
