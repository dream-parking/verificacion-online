package com.dreamparking.backend.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Versioned privacy notice; the hash identifies the exact text shown to the customer. */
@Entity
@Table(name = "aviso_privacidad")
public class PrivacyNotice {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Short id;

	@Column(name = "version", nullable = false, unique = true, length = 20)
	private String version;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "hash_texto", nullable = false, length = 64)
	private String textHash;

	@Column(name = "vigente_desde", nullable = false)
	private Instant validFrom;

	@Column(name = "vigente_hasta")
	private Instant validTo;

	public Short getId() {
		return id;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getTextHash() {
		return textHash;
	}

	public void setTextHash(String textHash) {
		this.textHash = textHash;
	}

	public Instant getValidFrom() {
		return validFrom;
	}

	public void setValidFrom(Instant validFrom) {
		this.validFrom = validFrom;
	}

	public Instant getValidTo() {
		return validTo;
	}

	public void setValidTo(Instant validTo) {
		this.validTo = validTo;
	}

}
