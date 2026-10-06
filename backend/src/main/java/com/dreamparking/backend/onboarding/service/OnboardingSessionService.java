package com.dreamparking.backend.onboarding.service;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.customer.entity.Device;
import com.dreamparking.backend.customer.service.DeviceService;
import com.dreamparking.backend.onboarding.dto.SessionResponse;
import com.dreamparking.backend.onboarding.dto.StartSessionRequest;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.onboarding.entity.OnboardingSession;
import com.dreamparking.backend.onboarding.repository.OnboardingSessionRepository;

/** Session and behavior signals (IP, device, typing pace) captured while the customer fills in a request. */
@Service
public class OnboardingSessionService {

	private final OnboardingSessionRepository sessions;

	private final OnboardingService onboardingService;

	private final DeviceService deviceService;

	public OnboardingSessionService(OnboardingSessionRepository sessions, OnboardingService onboardingService,
			DeviceService deviceService) {
		this.sessions = sessions;
		this.onboardingService = onboardingService;
		this.deviceService = deviceService;
	}

	/** Opens a session; the first session's device becomes the request's device. */
	@Transactional
	public SessionResponse start(UUID requestId, StartSessionRequest body, InetAddress ip, String userAgent) {
		OnboardingRequest request = onboardingService.findInProgress(requestId);
		Device device = deviceService.register(body.deviceFingerprint(), body.deviceModel(), body.operatingSystem());
		if (request.getDevice() == null) {
			request.setDevice(device);
		}

		OnboardingSession session = new OnboardingSession();
		session.setRequest(request);
		session.setDevice(device);
		session.setIp(ip);
		session.setUserAgent(userAgent);
		session.setAppVersion(body.appVersion());
		session.setApproximateLocation(body.approximateLocation());
		session.setCountryIso(body.countryIso());
		session.setTypingSpeedCpm(body.typingSpeedCpm());
		session.setTypingPace(body.typingPace());
		request.setLastActivityAt(Instant.now());
		return SessionResponse.of(sessions.save(session));
	}

	@Transactional
	public SessionResponse end(UUID requestId, UUID sessionId) {
		OnboardingSession session = sessions.findById(sessionId)
			.filter(s -> s.getRequest().getId().equals(requestId))
			.orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));
		if (session.getEndedAt() == null) {
			session.setEndedAt(Instant.now());
		}
		return SessionResponse.of(session);
	}

}
