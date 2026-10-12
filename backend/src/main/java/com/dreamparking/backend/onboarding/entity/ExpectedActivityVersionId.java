package com.dreamparking.backend.onboarding.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Primary key of {@link ExpectedActivityVersion}: one row per request and version number. */
@Embeddable
public class ExpectedActivityVersionId implements Serializable {

	@Column(name = "request_id")
	private UUID requestId;

	@Column(name = "version")
	private short version;

	protected ExpectedActivityVersionId() {
	}

	public ExpectedActivityVersionId(UUID requestId, short version) {
		this.requestId = requestId;
		this.version = version;
	}

	public UUID getRequestId() {
		return requestId;
	}

	public short getVersion() {
		return version;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof ExpectedActivityVersionId other && Objects.equals(requestId, other.requestId)
				&& version == other.version;
	}

	@Override
	public int hashCode() {
		return Objects.hash(requestId, version);
	}

}
