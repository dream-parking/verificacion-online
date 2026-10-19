package com.dreamparking.backend.liveness.service;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.dreamparking.backend.identity.ocr.DocumentPhoto;
import com.dreamparking.backend.identity.service.DocumentCipher;
import com.dreamparking.backend.identity.service.IdentityDocumentService;
import com.dreamparking.backend.liveness.client.LivenessClient;
import com.dreamparking.backend.liveness.client.LivenessException;
import com.dreamparking.backend.liveness.client.LivenessResult;
import com.dreamparking.backend.liveness.dto.LivenessCheckResponse;
import com.dreamparking.backend.liveness.entity.LivenessCheck;
import com.dreamparking.backend.liveness.entity.LivenessStatus;
import com.dreamparking.backend.liveness.repository.LivenessCheckRepository;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.onboarding.service.OnboardingService;

/**
 * Liveness check of the selfie (VDI-114): stores the selfie encrypted and records the confidence the provider gives.
 * A provider failure is not an error, so an outage never blocks the request: the check is recorded as
 * {@code FAILED} without confidence and the signal does not count in the score. The provider call runs outside the
 * database transaction so a slow answer does not hold a connection of the small pool.
 */
@Service
public class LivenessService {

	private static final Logger log = LoggerFactory.getLogger(LivenessService.class);

	private final OnboardingService onboardingService;

	private final LivenessCheckRepository checks;

	private final LivenessClient client;

	private final DocumentCipher cipher;

	private final TransactionTemplate transactions;

	public LivenessService(OnboardingService onboardingService, LivenessCheckRepository checks, LivenessClient client,
			DocumentCipher cipher, TransactionTemplate transactions) {
		this.onboardingService = onboardingService;
		this.checks = checks;
		this.client = client;
		this.cipher = cipher;
		this.transactions = transactions;
	}

	/**
	 * Checks the selfie and saves the answer. Can be repeated while the request is in progress; every try counts.
	 * @throws com.dreamparking.backend.common.exception.InvalidInputException when the selfie is empty or not a JPEG or PNG
	 * @throws com.dreamparking.backend.common.exception.InvalidStateException when the request is not in progress
	 */
	public LivenessCheckResponse check(UUID requestId, byte[] content) {
		DocumentPhoto selfie = IdentityDocumentService.photo("selfie", content);
		transactions.executeWithoutResult(status -> onboardingService.findInProgress(requestId));

		LivenessResult result = null;
		String failure = null;
		try {
			result = client.check(selfie);
		}
		catch (LivenessException ex) {
			log.warn("The liveness check of a request could not be done: {}", ex.getMessage());
			failure = ex.getMessage();
		}
		LivenessResult answer = result;
		String failed = failure;
		LivenessCheck check = transactions.execute(tx -> save(requestId, selfie, answer, failed));
		return LivenessCheckResponse.of(check);
	}

	private LivenessCheck save(UUID requestId, DocumentPhoto selfie, LivenessResult result, String failure) {
		OnboardingRequest request = onboardingService.findInProgress(requestId);
		Instant now = Instant.now();
		byte[] content = selfie.content();
		DocumentCipher.Sealed sealed = cipher.encrypt(content, LivenessCheck.associatedData(requestId));
		LivenessStatus status = result == null ? LivenessStatus.FAILED : LivenessStatus.CHECKED;
		BigDecimal confidence = result == null ? null : result.confidence();

		LivenessCheck check = checks.findById(requestId).orElseGet(() -> new LivenessCheck(request));
		check.record(status, client.provider(), confidence, failure, selfie.contentType(), sealed.iv(),
				sealed.ciphertext(), sealed.keyVersion(), sha256(content), content.length, now);
		checks.save(check);

		onboardingService.recordLivenessChecked(request, status);
		request.setLastActivityAt(now);
		return check;
	}

	private static String sha256(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}

}
