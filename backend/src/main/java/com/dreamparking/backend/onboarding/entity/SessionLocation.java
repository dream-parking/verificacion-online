package com.dreamparking.backend.onboarding.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dreamparking.backend.onboarding.entity.enums.LocationStatus;

/**
 * Approximate location of an onboarding session (VDI-41): coordinates only when {@code status} is
 * {@code AVAILABLE}. Shared by the session table and the console signals view, which use the same columns.
 */
@Embeddable
public class SessionLocation {

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "location_status", columnDefinition = "location_status")
	private LocationStatus status;

	@Column(name = "latitude", precision = 9, scale = 6)
	private BigDecimal latitude;

	@Column(name = "longitude", precision = 9, scale = 6)
	private BigDecimal longitude;

	@Column(name = "location_accuracy_m")
	private Integer accuracyMeters;

	protected SessionLocation() {
	}

	public SessionLocation(LocationStatus status, BigDecimal latitude, BigDecimal longitude, Integer accuracyMeters) {
		this.status = status;
		this.latitude = latitude;
		this.longitude = longitude;
		this.accuracyMeters = accuracyMeters;
	}

	public LocationStatus getStatus() {
		return status;
	}

	public BigDecimal getLatitude() {
		return latitude;
	}

	public BigDecimal getLongitude() {
		return longitude;
	}

	public Integer getAccuracyMeters() {
		return accuracyMeters;
	}

}
