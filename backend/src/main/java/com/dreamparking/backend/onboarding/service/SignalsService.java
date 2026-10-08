package com.dreamparking.backend.onboarding.service;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.dreamparking.backend.common.exception.InvalidInputException;
import com.dreamparking.backend.customer.entity.Device;
import com.dreamparking.backend.customer.repository.DeviceRepository;
import com.dreamparking.backend.onboarding.dto.CaptureSignalsRequest;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.onboarding.entity.OnboardingSession;
import com.dreamparking.backend.onboarding.entity.RequestStep;
import com.dreamparking.backend.onboarding.entity.RequestStepId;
import com.dreamparking.backend.onboarding.entity.SessionGeolocation;
import com.dreamparking.backend.onboarding.entity.enums.TypingPace;
import com.dreamparking.backend.onboarding.geo.GeolocationException;
import com.dreamparking.backend.onboarding.geo.IpGeolocationClient;
import com.dreamparking.backend.onboarding.repository.OnboardingSessionRepository;
import com.dreamparking.backend.onboarding.repository.RequestStepRepository;

/** Stores the device and behavior signals captured while the customer fills in a request (VDI-41, VDI-42, VDI-67). */
@Service
public class SignalsService {

	private static final Logger log = LoggerFactory.getLogger(SignalsService.class);

	/** Provisional cut-offs for the typing pace category, in characters per minute. */
	static final int SLOW_BELOW_CPM = 120;

	static final int FAST_ABOVE_CPM = 250;

	/** Tolerated difference between the phone clock and the server clock for step times. */
	static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

	private final OnboardingService onboardingService;

	private final DeviceRepository devices;

	private final OnboardingSessionRepository sessions;

	private final RequestStepRepository steps;

	private final IpGeolocationClient geolocation;

	private final TransactionTemplate transactions;

	public SignalsService(OnboardingService onboardingService, DeviceRepository devices,
			OnboardingSessionRepository sessions, RequestStepRepository steps, IpGeolocationClient geolocation,
			TransactionTemplate transactions) {
		this.onboardingService = onboardingService;
		this.devices = devices;
		this.sessions = sessions;
		this.steps = steps;
		this.geolocation = geolocation;
		this.transactions = transactions;
	}

	/**
	 * Saves (or replaces) the signals of the request's session; the same device is reused across requests. The
	 * location comes from the client IP, resolved by an external service: that call runs outside the database
	 * transaction so a slow provider does not hold a connection of the small pool.
	 */
	public void capture(UUID requestId, CaptureSignalsRequest body, String clientIp, String userAgent) {
		InetAddress ip = parse(clientIp);
		transactions.executeWithoutResult(status -> onboardingService.findInProgress(requestId));
		SessionGeolocation located = locate(ip);
		transactions.executeWithoutResult(status -> save(requestId, body, ip, userAgent, located));
	}

	private SessionGeolocation locate(InetAddress ip) {
		Instant now = Instant.now();
		if (!isPublic(ip)) {
			return SessionGeolocation.unavailable("private or reserved address", now);
		}
		try {
			return SessionGeolocation.available(geolocation.locate(ip), now);
		}
		catch (GeolocationException ex) {
			log.warn("Location of a session is unavailable: {}", ex.getMessage());
			return SessionGeolocation.unavailable(ex.getMessage(), now);
		}
	}

	private void save(UUID requestId, CaptureSignalsRequest body, InetAddress ip, String userAgent,
			SessionGeolocation located) {
		OnboardingRequest request = onboardingService.findInProgress(requestId);
		Instant now = Instant.now();

		Device device = devices.findByFingerprint(body.deviceFingerprint()).orElseGet(Device::new);
		device.setFingerprint(body.deviceFingerprint());
		if (body.deviceModel() != null) {
			device.setModel(body.deviceModel());
		}
		if (body.operatingSystem() != null) {
			device.setOperatingSystem(body.operatingSystem());
		}
		device.setLastSeenAt(now);
		device = devices.save(device);
		request.setDevice(device);

		OnboardingSession session = sessions.findFirstByRequestIdOrderByStartedAtDesc(requestId)
			.orElseGet(OnboardingSession::new);
		session.setRequest(request);
		session.setDevice(device);
		boolean keepKnownLocation = !located.isAvailable() && session.getGeolocation().isAvailable()
				&& ip.equals(session.getIp());
		session.setIp(ip);
		session.setUserAgent(userAgent);
		session.setAppVersion(body.appVersion());
		// A failed lookup (rate limit, timeout) must not erase a location already found for the same IP.
		if (!keepKnownLocation) {
			session.setGeolocation(located);
		}
		// What the server resolved wins over what the app says; the app's text is the fallback.
		SessionGeolocation geo = session.getGeolocation();
		session.setApproximateLocation(geo.isAvailable() && geo.describe() != null ? geo.describe()
				: body.approximateLocation());
		session.setCountryIso(geo.isAvailable() && geo.getCountryCode() != null ? geo.getCountryCode()
				: (body.countryIso() == null ? null : body.countryIso().toUpperCase()));
		session.setTypingSpeedCpm(body.typingSpeedCpm());
		session.setTypingPace(paceOf(body.typingSpeedCpm()));
		sessions.save(session);

		if (body.steps() != null) {
			body.steps().forEach(timing -> saveStep(request, timing, now));
		}
		request.setLastActivityAt(now);
	}

	static TypingPace paceOf(Short cpm) {
		if (cpm == null) {
			return null;
		}
		if (cpm < SLOW_BELOW_CPM) {
			return TypingPace.SLOW;
		}
		return cpm > FAST_ABOVE_CPM ? TypingPace.FAST : TypingPace.NORMAL;
	}

	/**
	 * The app is the only source of step times and attempts (it measures from when the customer sees the screen).
	 * Times are checked against the request and the server clock, so a wrong phone clock cannot put impossible
	 * values in the KYC file.
	 */
	private void saveStep(OnboardingRequest request, CaptureSignalsRequest.StepTiming timing, Instant now) {
		if (timing.completedAt() != null && timing.completedAt().isBefore(timing.startedAt())) {
			throw new InvalidInputException("Step " + timing.step() + " completes before it starts");
		}
		if (timing.startedAt().isBefore(request.getStartedAt().minus(MAX_CLOCK_SKEW))) {
			throw new InvalidInputException("Step " + timing.step() + " starts before the request was created");
		}
		Instant latest = now.plus(MAX_CLOCK_SKEW);
		if (timing.startedAt().isAfter(latest) || (timing.completedAt() != null && timing.completedAt().isAfter(latest))) {
			throw new InvalidInputException("Step " + timing.step() + " has a time in the future");
		}
		RequestStep step = steps.findById(new RequestStepId(request.getId(), timing.step()))
			.orElseGet(() -> new RequestStep(request, timing.step(), timing.startedAt()));
		step.setStartedAt(timing.startedAt());
		step.setCompletedAt(timing.completedAt());
		if (timing.attempts() != null) {
			step.setAttempts(timing.attempts());
		}
		steps.save(step);
	}

	/** False for loopback, private, link-local and other addresses that no provider can locate. */
	static boolean isPublic(InetAddress ip) {
		if (ip.isAnyLocalAddress() || ip.isLoopbackAddress() || ip.isLinkLocalAddress() || ip.isSiteLocalAddress()
				|| ip.isMulticastAddress()) {
			return false;
		}
		byte[] bytes = ip.getAddress();
		if (ip instanceof Inet6Address) {
			return (bytes[0] & 0xfe) != 0xfc; // unique local fc00::/7
		}
		return !((bytes[0] & 0xff) == 100 && (bytes[1] & 0xc0) == 64); // carrier-grade NAT 100.64.0.0/10
	}

	private static InetAddress parse(String address) {
		try {
			return InetAddress.getByName(address);
		}
		catch (UnknownHostException ex) {
			throw new InvalidInputException("Cannot determine the client IP address");
		}
	}

}
