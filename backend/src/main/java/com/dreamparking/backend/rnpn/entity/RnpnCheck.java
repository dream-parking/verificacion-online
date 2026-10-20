package com.dreamparking.backend.rnpn.entity;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

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

import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.rnpn.client.RnpnClient;
import com.dreamparking.backend.rnpn.entity.enums.RnpnField;
import com.dreamparking.backend.rnpn.entity.enums.RnpnOutcome;

/**
 * One check of the DUI data against the RNPN (VDI-111): the result, how many tries it took, the provider and, when the
 * data did not match, which fields. The file only receives new checks: the database refuses to change or delete one.
 */
@Entity
@Table(name = "rnpn_check")
public class RnpnCheck {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id", nullable = false, updatable = false)
	private OnboardingRequest request;

	@Enumerated(EnumType.STRING)
	@Column(name = "outcome", nullable = false, length = 12, updatable = false)
	private RnpnOutcome outcome;

	@Column(name = "provider", nullable = false, length = 60, updatable = false)
	private String provider;

	@Column(name = "attempts", nullable = false, updatable = false)
	private short attempts;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "mismatched_fields", nullable = false, updatable = false)
	private String[] mismatchedFields = new String[0];

	@Column(name = "failure", length = 250, updatable = false)
	private String failure;

	@CreationTimestamp
	@Column(name = "checked_at", nullable = false, updatable = false)
	private Instant checkedAt;

	protected RnpnCheck() {
	}

	public RnpnCheck(OnboardingRequest request, RnpnOutcome outcome, String provider, int attempts,
			List<RnpnField> mismatchedFields, String failure) {
		this.request = request;
		this.outcome = outcome;
		this.provider = provider;
		this.attempts = (short) attempts;
		this.mismatchedFields = mismatchedFields.stream().map(Enum::name).toArray(String[]::new);
		this.failure = failure == null || failure.length() <= 250 ? failure : failure.substring(0, 250);
	}

	public Long getId() {
		return id;
	}

	public OnboardingRequest getRequest() {
		return request;
	}

	public RnpnOutcome getOutcome() {
		return outcome;
	}

	public String getProvider() {
		return provider;
	}

	public short getAttempts() {
		return attempts;
	}

	public List<RnpnField> getMismatchedFields() {
		return Arrays.stream(mismatchedFields).map(RnpnField::valueOf).toList();
	}

	public String getFailure() {
		return failure;
	}

	public Instant getCheckedAt() {
		return checkedAt;
	}

	/** True while the RNPN is the simulated service, not the real one. */
	public boolean isSimulated() {
		return RnpnClient.SIMULATED.equals(provider);
	}

}
