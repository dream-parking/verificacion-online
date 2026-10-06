package com.dreamparking.backend.onboarding.entity;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dreamparking.backend.catalog.entity.PrivacyNotice;

/** Privacy notice acceptance (step 1), one per request. */
@Entity
@Table(name = "consentimiento_privacidad")
public class PrivacyConsent {

	@Id
	@Column(name = "solicitud_id")
	private UUID requestId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "solicitud_id")
	private OnboardingRequest request;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "aviso_id", nullable = false)
	private PrivacyNotice notice;

	@Column(name = "acepta_senales", nullable = false)
	private Boolean signalsAccepted;

	@CreationTimestamp
	@Column(name = "aceptado_en", nullable = false)
	private Instant acceptedAt;

	@JdbcTypeCode(SqlTypes.INET)
	@Column(name = "ip", nullable = false)
	private InetAddress ip;

	public UUID getRequestId() {
		return requestId;
	}

	public OnboardingRequest getRequest() {
		return request;
	}

	public void setRequest(OnboardingRequest request) {
		this.request = request;
	}

	public PrivacyNotice getNotice() {
		return notice;
	}

	public void setNotice(PrivacyNotice notice) {
		this.notice = notice;
	}

	public Boolean getSignalsAccepted() {
		return signalsAccepted;
	}

	public void setSignalsAccepted(Boolean signalsAccepted) {
		this.signalsAccepted = signalsAccepted;
	}

	public Instant getAcceptedAt() {
		return acceptedAt;
	}

	public InetAddress getIp() {
		return ip;
	}

	public void setIp(InetAddress ip) {
		this.ip = ip;
	}

}
