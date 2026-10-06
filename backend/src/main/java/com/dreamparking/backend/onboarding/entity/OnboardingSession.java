package com.dreamparking.backend.onboarding.entity;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dreamparking.backend.common.persistence.PgEnumJdbcType;
import com.dreamparking.backend.customer.entity.Device;
import com.dreamparking.backend.onboarding.entity.enums.TypingPace;

/** Session and behavior signals captured while the customer fills in a request. */
@Entity
@Table(name = "sesion_onboarding")
public class OnboardingSession {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id")
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "solicitud_id", nullable = false)
	private OnboardingRequest request;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "dispositivo_id", nullable = false)
	private Device device;

	@JdbcTypeCode(SqlTypes.INET)
	@Column(name = "ip", nullable = false)
	private InetAddress ip;

	@Column(name = "ubicacion_aprox", length = 120)
	private String approximateLocation;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "pais_iso", length = 2)
	private String countryIso;

	@Column(name = "user_agent", columnDefinition = "text")
	private String userAgent;

	@Column(name = "app_version", length = 20)
	private String appVersion;

	@Column(name = "ritmo_cpm")
	private Short typingSpeedCpm;

	@Convert(converter = TypingPace.JpaConverter.class)
	@JdbcType(PgEnumJdbcType.class)
	@Column(name = "ritmo_categoria", columnDefinition = "ritmo_escritura")
	private TypingPace typingPace;

	@CreationTimestamp
	@Column(name = "iniciada_en", nullable = false)
	private Instant startedAt;

	@Column(name = "finalizada_en")
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
