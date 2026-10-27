package com.dreamparking.backend.signature.entity;

import java.net.InetAddress;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.signature.client.ContractSigner;

/**
 * The electronic signature of the account contract of a request (VDI-130), with its evidence: which version and the
 * fingerprint of the exact text, when, by which provider, and the device and IP of the session. One per request; the
 * database refuses to change or delete it.
 */
@Entity
@Table(name = "contract_signature")
public class ContractSignature {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id", nullable = false, unique = true, updatable = false)
	private OnboardingRequest request;

	@Column(name = "contract_version", nullable = false, length = 30, updatable = false)
	private String contractVersion;

	@Column(name = "contract_fingerprint", nullable = false, length = 64, updatable = false)
	private String contractFingerprint;

	@Column(name = "provider", nullable = false, length = 60, updatable = false)
	private String provider;

	@Column(name = "ip", updatable = false)
	private InetAddress ip;

	@Column(name = "device", columnDefinition = "text", updatable = false)
	private String device;

	@Column(name = "device_fingerprint", length = 64, updatable = false)
	private String deviceFingerprint;

	@CreationTimestamp
	@Column(name = "signed_at", nullable = false, updatable = false)
	private Instant signedAt;

	protected ContractSignature() {
	}

	public ContractSignature(OnboardingRequest request, String contractVersion, String contractFingerprint,
			String provider, InetAddress ip, String device, String deviceFingerprint) {
		this.request = request;
		this.contractVersion = contractVersion;
		this.contractFingerprint = contractFingerprint;
		this.provider = provider;
		this.ip = ip;
		this.device = device;
		this.deviceFingerprint = deviceFingerprint;
	}

	public Long getId() {
		return id;
	}

	public OnboardingRequest getRequest() {
		return request;
	}

	public String getContractVersion() {
		return contractVersion;
	}

	public String getContractFingerprint() {
		return contractFingerprint;
	}

	public String getProvider() {
		return provider;
	}

	public InetAddress getIp() {
		return ip;
	}

	public String getDevice() {
		return device;
	}

	public String getDeviceFingerprint() {
		return deviceFingerprint;
	}

	public Instant getSignedAt() {
		return signedAt;
	}

	/** True while the signature service is the simulated one, not a real provider. */
	public boolean isSimulated() {
		return ContractSigner.SIMULATED.equals(provider);
	}

}
