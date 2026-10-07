package com.dreamparking.backend.onboarding.entity;

import java.net.InetAddress;
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

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dreamparking.backend.customer.entity.Device;
import com.dreamparking.backend.onboarding.entity.enums.TypingPace;

/** Session and behavior signals captured while the customer fills in a request. */
@Entity
@Table(name = "onboarding_session")
public class OnboardingSession {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id")
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id", nullable = false)
	private OnboardingRequest request;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "device_id", nullable = false)
	private Device device;

	@JdbcTypeCode(SqlTypes.INET)
	@Column(name = "ip", nullable = false)
	private InetAddress ip;

	@Column(name = "approximate_location", length = 120)
	private String approximateLocation;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "country_iso", length = 2)
	private String countryIso;

	@Column(name = "user_agent", columnDefinition = "text")
	private String userAgent;

	@Column(name = "app_version", length = 20)
	private String appVersion;

	@Column(name = "typing_speed_cpm")
	private Short typingSpeedCpm;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "typing_pace", columnDefinition = "typing_pace")
	private TypingPace typingPace;

	@CreationTimestamp
	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "ended_at")
	private Instant endedAt;

	public UUID getId() {
		return id;
	}

	public OnboardingRequest getRequest() {
		return request;
	}

	public void setRequest(OnboardingRequest request) {
		this.request = request;
	}

	public Device getDevice() {
		return device;
	}

	public void setDevice(Device device) {
		this.device = device;
	}

	public InetAddress getIp() {
		return ip;
	}

	public void setIp(InetAddress ip) {
		this.ip = ip;
	}

	public String getApproximateLocation() {
		return approximateLocation;
	}

	public void setApproximateLocation(String approximateLocation) {
		this.approximateLocation = approximateLocation;
	}

	public String getCountryIso() {
		return countryIso;
	}

	public void setCountryIso(String countryIso) {
		this.countryIso = countryIso;
	}

	public String getUserAgent() {
		return userAgent;
	}

	public void setUserAgent(String userAgent) {
		this.userAgent = userAgent;
	}

	public String getAppVersion() {
		return appVersion;
	}

	public void setAppVersion(String appVersion) {
		this.appVersion = appVersion;
	}

	public Short getTypingSpeedCpm() {
		return typingSpeedCpm;
	}

	public void setTypingSpeedCpm(Short typingSpeedCpm) {
		this.typingSpeedCpm = typingSpeedCpm;
	}

	public TypingPace getTypingPace() {
		return typingPace;
	}

	public void setTypingPace(TypingPace typingPace) {
		this.typingPace = typingPace;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getEndedAt() {
		return endedAt;
	}

	public void setEndedAt(Instant endedAt) {
		this.endedAt = endedAt;
	}

}
