package com.dreamparking.backend.onboarding.entity;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Synchronize;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dreamparking.backend.common.persistence.PgEnumJdbcType;
import com.dreamparking.backend.onboarding.entity.enums.TypingPace;

/** Session and device signals of a request, as shown in the console (view). */
@Entity
@Immutable
// Tables the view reads: Hibernate flushes pending changes to them before querying it.
@Synchronize({ "solicitud", "dispositivo", "sesion_onboarding", "paso_solicitud" })
@Table(name = "v_solicitud_senales")
public class RequestSignals {

	@Id
	@Column(name = "solicitud_id")
	private UUID requestId;

	@JdbcTypeCode(SqlTypes.INET)
	@Column(name = "ip")
	private InetAddress ip;

	@Column(name = "ubicacion_aprox", length = 120)
	private String approximateLocation;

	@Column(name = "huella", length = 64)
	private String deviceFingerprint;

	@Column(name = "dispositivo", columnDefinition = "text")
	private String device;

	@Column(name = "ritmo_cpm")
	private Short typingSpeedCpm;

	@Convert(converter = TypingPace.JpaConverter.class)
	@JdbcType(PgEnumJdbcType.class)
	@Column(name = "ritmo_categoria", columnDefinition = "ritmo_escritura")
	private TypingPace typingPace;

	@Column(name = "iniciada_en")
	private Instant startedAt;

	@Column(name = "enviada_en")
	private Instant submittedAt;

	@Column(name = "horario_nocturno")
	private Boolean nightTime;

	@Column(name = "duracion_total_seg")
	private Long totalDurationSeconds;

	@Column(name = "solicitudes_mismo_dispositivo")
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
