package com.dreamparking.backend.onboarding.entity;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Synchronize;
import org.hibernate.type.SqlTypes;

import com.dreamparking.backend.onboarding.entity.enums.LocationStatus;
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

	@Enumerated(EnumType.STRING)
	@Column(name = "location_status", length = 12)
	private LocationStatus locationStatus;

	@Column(name = "geo_latitude")
	private BigDecimal latitude;

	@Column(name = "geo_longitude")
	private BigDecimal longitude;

	@Column(name = "geo_country")
	private String country;

	@Column(name = "geo_country_code", length = 2)
	@JdbcTypeCode(SqlTypes.CHAR)
	private String countryCode;

	@Column(name = "geo_region")
	private String region;

	@Column(name = "geo_region_name")
	private String regionName;

	@Column(name = "geo_city")
	private String city;

	@Column(name = "geo_zip")
	private String zip;

	@Column(name = "geo_timezone")
	private String timezone;

	@Column(name = "geo_isp")
	private String isp;

	@Column(name = "geo_org")
	private String org;

	@Column(name = "geo_as")
	private String asName;

	@Column(name = "geo_failure")
	private String failure;

	@Column(name = "geo_looked_up_at")
	private Instant lookedUpAt;

	public UUID getRequestId() {
		return requestId;
	}

	public InetAddress getIp() {
		return ip;
	}

	public String getApproximateLocation() {
		return approximateLocation;
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

	public LocationStatus getLocationStatus() {
		return locationStatus;
	}

	public BigDecimal getLatitude() {
		return latitude;
	}

	public BigDecimal getLongitude() {
		return longitude;
	}

	public String getCountry() {
		return country;
	}

	public String getCountryCode() {
		return countryCode;
	}

	public String getRegion() {
		return region;
	}

	public String getRegionName() {
		return regionName;
	}

	public String getCity() {
		return city;
	}

	public String getZip() {
		return zip;
	}

	public String getTimezone() {
		return timezone;
	}

	public String getIsp() {
		return isp;
	}

	public String getOrg() {
		return org;
	}

	public String getAsName() {
		return asName;
	}

	public String getFailure() {
		return failure;
	}

	public Instant getLookedUpAt() {
		return lookedUpAt;
	}

}
