package com.dreamparking.backend.identity.entity;

import java.time.Instant;
import java.util.UUID;

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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dreamparking.backend.identity.entity.enums.DocumentSide;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;

/**
 * Encrypted photo of one side of the DUI (VDI-80). Only {@code DocumentCipher} can read {@code ciphertext}; a new
 * capture replaces the photo while the request is in progress, and the database blocks changes after that.
 */
@Entity
@Table(name = "identity_document_image")
public class IdentityDocumentImage {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id")
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id", nullable = false)
	private OnboardingRequest request;

	@Enumerated(EnumType.STRING)
	@Column(name = "side", nullable = false, length = 5)
	private DocumentSide side;

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

	@Column(name = "captured_at", nullable = false)
	private Instant capturedAt;

	protected IdentityDocumentImage() {
	}

	public IdentityDocumentImage(OnboardingRequest request, DocumentSide side) {
		this.request = request;
		this.side = side;
	}

	/** Associated data of the encryption: the photo only decrypts in its own row. */
	public static String associatedData(UUID requestId, DocumentSide side) {
		return requestId + ":" + side;
	}

	/** Replaces the photo with a new capture. */
	public void store(String contentType, byte[] iv, byte[] ciphertext, short keyVersion, String sha256,
			int sizeBytes, Instant capturedAt) {
		this.contentType = contentType;
		this.iv = iv.clone();
		this.ciphertext = ciphertext.clone();
		this.keyVersion = keyVersion;
		this.sha256 = sha256;
		this.sizeBytes = sizeBytes;
		this.capturedAt = capturedAt;
	}

	public UUID getId() {
		return id;
	}

	public OnboardingRequest getRequest() {
		return request;
	}

	public DocumentSide getSide() {
		return side;
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

	public Instant getCapturedAt() {
		return capturedAt;
	}

}
