package com.dreamparking.backend.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An image encrypted by {@code DocumentCipher}, with what is needed to decrypt and compare it: the DUI photos
 * (VDI-80) and the selfie of the liveness check (VDI-114) share these columns. Only {@code DocumentCipher} can read
 * {@code ciphertext}. {@code sha256} is the hash of the original image, so the same photo used in two requests is a
 * fraud signal.
 */
@Embeddable
public class EncryptedImage {

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

	protected EncryptedImage() {
	}

	public EncryptedImage(String contentType, byte[] iv, byte[] ciphertext, short keyVersion, String sha256,
			int sizeBytes) {
		this.contentType = contentType;
		this.iv = iv.clone();
		this.ciphertext = ciphertext.clone();
		this.keyVersion = keyVersion;
		this.sha256 = sha256;
		this.sizeBytes = sizeBytes;
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

}
