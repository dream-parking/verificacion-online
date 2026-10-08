package com.dreamparking.backend.onboarding.entity;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Synchronize;
import org.hibernate.type.SqlTypes;

import com.dreamparking.backend.onboarding.entity.enums.TypingPace;

/** Session and device signals of a request, as shown in the console (view). */
@Entity
@Immutable
// Tables the view reads: Hibernate flushes pending changes to them before querying it.
@Synchronize({ "onboarding_request", "device", "onboarding_session", "request_step" })
@Table(name = "v_request_signals")
public class RequestSignals {

	@Id
	@Column(name = "request_id")
	private UUID requestId;

	@JdbcTypeCode(SqlTypes.INET)
	@Column(name = "ip")
	private InetAddress ip;

	@Column(name = "approximate_location", length = 120)
	private String approximateLocation;

	/** VDI-41. Null when the app did not report the location (Hibernate leaves it null when all its columns are). */
	@Embedded
	private SessionLocation location;

	@Column(name = "device_fingerprint", length = 64)
	private String deviceFingerprint;

	@Column(name = "device", columnDefinition = "text")
	private String device;

	@Column(name = "typing_speed_cpm")
	private Short typingSpeedCpm;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "typing_pace", columnDefinition = "typing_pace")
	private TypingPace typingPace;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "submitted_at")
	private Instant submittedAt;

	@Column(name = "night_time")
	private Boolean nightTime;

	@Column(name = "total_duration_seconds")
	private Long totalDurationSeconds;

	@Column(name = "requests_from_same_device")
	private Long requestsFromSameDevice;

	public UUID getRequestId() {
		return requestId;
	}

	public InetAddress getIp() {
		return ip;
	}

	public String getApproximateLocation() {
		return approximateLocation;
	}

	public SessionLocation getLocation() {
		return location;
	}

	public String getDeviceFingerprint() {
		return deviceFingerprint;
	}

	public String getDevice() {
		return device;
	}

	public Short getTypingSpeedCpm() {
		return typingSpeedCpm;
	}

	public TypingPace getTypingPace() {
		return typingPace;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getSubmittedAt() {
		return submittedAt;
	}

	public Boolean getNightTime() {
		return nightTime;
	}

	public Long getTotalDurationSeconds() {
		return totalDurationSeconds;
	}

	public Long getRequestsFromSameDevice() {
		return requestsFromSameDevice;
	}

}
