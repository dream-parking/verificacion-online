package com.dreamparking.backend.onboarding.service;

import com.dreamparking.backend.onboarding.entity.enums.LocationStatus;
import java.math.RoundingMode;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.common.exception.InvalidInputException;
import com.dreamparking.backend.customer.entity.Device;
import com.dreamparking.backend.customer.repository.DeviceRepository;
import com.dreamparking.backend.onboarding.dto.CaptureSignalsRequest;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.onboarding.entity.OnboardingSession;
import com.dreamparking.backend.onboarding.entity.RequestStep;
import com.dreamparking.backend.onboarding.entity.RequestStepId;
import com.dreamparking.backend.onboarding.entity.enums.TypingPace;
import com.dreamparking.backend.onboarding.repository.OnboardingSessionRepository;
import com.dreamparking.backend.onboarding.repository.RequestStepRepository;

/** Stores the device and behavior signals captured while the customer fills in a request (VDI-41, VDI-42). */
@Service
public class SignalsService {

	/** Provisional cut-offs for the typing pace category, in characters per minute. */
	static final int SLOW_BELOW_CPM = 120;

	static final int FAST_ABOVE_CPM = 250;

	/** Tolerated difference between the phone clock and the server clock for step times. */
	static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

	private final OnboardingService onboardingService;

	private final DeviceRepository devices;

	private final OnboardingSessionRepository sessions;

	private final RequestStepRepository steps;

	public SignalsService(OnboardingService onboardingService, DeviceRepository devices,
			OnboardingSessionRepository sessions, RequestStepRepository steps) {
		this.onboardingService = onboardingService;
		this.devices = devices;
		this.sessions = sessions;
		this.steps = steps;
	}

	/** Saves (or replaces) the signals of the request's session; the same device is reused across requests. */
	@Transactional
	public void capture(UUID requestId, CaptureSignalsRequest body, String clientIp, String userAgent) {
		OnboardingRequest request = onboardingService.findInProgress(requestId);
		checkLocation(body);
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
		session.setIp(parse(clientIp));
		session.setUserAgent(userAgent);
		session.setAppVersion(body.appVersion());
		session.setApproximateLocation(body.approximateLocation());
		session.setCountryIso(body.countryIso() == null ? null : body.countryIso().toUpperCase());
		session.setLocation(body.locationStatus(), coordinate(body.latitude()), coordinate(body.longitude()),
				body.locationAccuracyMeters());
		session.setTypingSpeedCpm(body.typingSpeedCpm());
		session.setTypingPace(paceOf(body.typingSpeedCpm()));
		sessions.save(session);

		if (body.steps() != null) {
			body.steps().forEach(timing -> saveStep(request, timing, now));
		}
		request.setLastActivityAt(now);
	}

	/**
	 * VDI-41: coordinates travel only with {@code AVAILABLE}, which needs both of them. Without permission
	 * ({@code PERMISSION_DENIED}) or without a position ({@code UNAVAILABLE}) the location is "not available".
	 */
	private static void checkLocation(CaptureSignalsRequest body) {
		if (body.locationStatus() == LocationStatus.AVAILABLE) {
			if (body.latitude() == null || body.longitude() == null) {
				throw new InvalidInputException("locationStatus AVAILABLE needs latitude and longitude");
			}
		}
		else if (body.latitude() != null || body.longitude() != null || body.locationAccuracyMeters() != null) {
			throw new InvalidInputException(
					"latitude, longitude and locationAccuracyMeters are only sent with locationStatus AVAILABLE");
		}
	}

	/** Six decimals (about 0.1 m), the precision of the column. */
	private static BigDecimal coordinate(BigDecimal value) {
		return value == null ? null : value.setScale(6, RoundingMode.HALF_UP);
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

	private static InetAddress parse(String address) {
		try {
			return InetAddress.getByName(address);
		}
		catch (UnknownHostException ex) {
			throw new InvalidInputException("Cannot determine the client IP address");
		}
	}

}
