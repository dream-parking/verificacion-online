package com.dreamparking.backend.onboarding.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dreamparking.backend.onboarding.entity.enums.LocationStatus;
import com.dreamparking.backend.onboarding.geo.IpGeolocation;

/** Location of a session as resolved by the server from the client IP (VDI-67). */
@Embeddable
public class SessionGeolocation {

	@Enumerated(EnumType.STRING)
	@Column(name = "location_status", nullable = false, length = 12)
	private LocationStatus status = LocationStatus.UNAVAILABLE;

	@Column(name = "geo_country", length = 80)
	private String country;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "geo_country_code", length = 2)
	private String countryCode;

	@Column(name = "geo_region", length = 10)
	private String region;

	@Column(name = "geo_region_name", length = 80)
	private String regionName;

	@Column(name = "geo_city", length = 80)
	private String city;

	@Column(name = "geo_zip", length = 20)
	private String zip;

	@Column(name = "geo_latitude", precision = 9, scale = 6)
	private BigDecimal latitude;

	@Column(name = "geo_longitude", precision = 9, scale = 6)
	private BigDecimal longitude;

	@Column(name = "geo_timezone", length = 60)
	private String timezone;

	@Column(name = "geo_isp", length = 120)
	private String isp;

	@Column(name = "geo_org", length = 120)
	private String org;

	@Column(name = "geo_as", length = 120)
	private String asName;

	@Column(name = "geo_failure", length = 120)
	private String failure;

	@Column(name = "geo_looked_up_at")
	private Instant lookedUpAt;

	public static SessionGeolocation available(IpGeolocation located, Instant at) {
		SessionGeolocation geo = new SessionGeolocation();
		geo.status = LocationStatus.AVAILABLE;
		geo.country = located.country();
		geo.countryCode = located.countryCode() == null ? null : located.countryCode().toUpperCase();
		geo.region = located.region();
		geo.regionName = located.regionName();
		geo.city = located.city();
		geo.zip = located.zip();
		geo.latitude = BigDecimal.valueOf(located.lat()).setScale(6, RoundingMode.HALF_UP);
		geo.longitude = BigDecimal.valueOf(located.lon()).setScale(6, RoundingMode.HALF_UP);
		geo.timezone = located.timezone();
		geo.isp = located.isp();
		geo.org = located.org();
		geo.asName = located.asName();
		geo.lookedUpAt = at;
		return geo;
	}

	public static SessionGeolocation unavailable(String reason, Instant at) {
		SessionGeolocation geo = new SessionGeolocation();
		geo.failure = reason == null ? null : reason.substring(0, Math.min(reason.length(), 120));
		geo.lookedUpAt = at;
		return geo;
	}

	public boolean isAvailable() {
		return status == LocationStatus.AVAILABLE;
	}

	/** "City, Region, Country" with whatever parts the provider returned; null when there is none. */
	public String describe() {
		String text = java.util.stream.Stream.of(city, regionName, country)
			.filter(part -> part != null && !part.isBlank())
			.distinct()
			.collect(java.util.stream.Collectors.joining(", "));
		return text.isEmpty() ? null : text;
	}

	public LocationStatus getStatus() {
		return status;
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

	public BigDecimal getLatitude() {
		return latitude;
	}

	public BigDecimal getLongitude() {
		return longitude;
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
