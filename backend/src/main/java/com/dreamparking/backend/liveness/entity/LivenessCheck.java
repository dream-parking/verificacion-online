package com.dreamparking.backend.liveness.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.dreamparking.backend.onboarding.entity.OnboardingRequest;

/**
 * The request's liveness check (VDI-114), one per request: the last attempt, with the provider's confidence and the
 * selfie encrypted by {@code DocumentCipher}. A new attempt replaces the answer and the selfie while the request is
 * in progress, but {@code attempts} keeps counting; the database blocks any change after that.
 */
@Entity
@Table(name = "liveness_check")
public class LivenessCheck {

	@Id
	@Column(name = "request_id")
	private UUID requestId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id")
	private OnboardingRequest request;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 10)
	private LivenessStatus status;

	@Column(name = "provider", nullable = false, length = 60)
	private String provider;

	/** From 0 to 1; null when the provider failed. */
	@Column(name = "confidence", precision = 3, scale = 2)
	private BigDecimal confidence;

	@Column(name = "attempts", nullable = false)
	private short attempts;

	@Column(name = "failure", length = 250)
	private String failure;

	@Column(name = "content_type", nullable = false, length = 20)
	private String contentType;

	@Column(name = "ciphertext", nullable = false)
	private byte[] ciphertext;

	@Column(name = "iv", nullable = false)
	private byte[] iv;

	@Column(name = "key_version", nullable = false)
	private short keyVersion;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "sha256", nullable = false, length = 64)
	private String sha256;

	@Column(name = "size_bytes", nullable = false)
	private int sizeBytes;

	@Column(name = "checked_at", nullable = false)
	private Instant checkedAt;

	@CreationTimestamp
	@Column(name = "registered_at", nullable = false, updatable = false)
	private Instant registeredAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected LivenessCheck() {
	}

	public LivenessCheck(OnboardingRequest request) {
		this.request = request;
	}

	/** Associated data of the encryption: the selfie only decrypts in its own row. */
	public static String associatedData(UUID requestId) {
		return requestId + ":SELFIE";
	}

	/** Records an attempt: replaces the answer and the selfie, and counts one more try. */
	public void record(LivenessStatus status, String provider, BigDecimal confidence, String failure,
			String contentType, byte[] iv, byte[] ciphertext, short keyVersion, String sha256, int sizeBytes,
			Instant now) {
		this.attempts = (short) (this.attempts + 1);
		this.status = status;
		this.provider = provider;
		this.confidence = confidence;
		this.failure = failure == null || failure.length() <= 250 ? failure : failure.substring(0, 250);
		this.contentType = contentType;
		this.iv = iv.clone();
		this.ciphertext = ciphertext.clone();
		this.keyVersion = keyVersion;
		this.sha256 = sha256;
		this.sizeBytes = sizeBytes;
		this.checkedAt = now;
	}

	public UUID getRequestId() {
		return requestId;
	}

	public OnboardingRequest getRequest() {
		return request;
	}

	public LivenessStatus getStatus() {
		return status;
	}

	public String getProvider() {
		return provider;
	}

	public BigDecimal getConfidence() {
		return confidence;
	}

	public short getAttempts() {
		return attempts;
	}

	public String getFailure() {
		return failure;
	}

	public String getContentType() {
		return contentType;
	}

	public byte[] getCiphertext() {
		return ciphertext.clone();
	}

	public byte[] getIv() {
		return iv.clone();
	}

	public short getKeyVersion() {
		return keyVersion;
	}

	public String getSha256() {
		return sha256;
	}

	public int getSizeBytes() {
		return sizeBytes;
	}

	public Instant getCheckedAt() {
		return checkedAt;
	}

}
