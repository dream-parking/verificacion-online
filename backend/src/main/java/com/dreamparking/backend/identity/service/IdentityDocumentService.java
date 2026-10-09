package com.dreamparking.backend.identity.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.dreamparking.backend.common.exception.InvalidInputException;
import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.identity.dto.IdentityDocumentResponse;
import com.dreamparking.backend.identity.entity.IdentityDocument;
import com.dreamparking.backend.identity.entity.IdentityDocumentImage;
import com.dreamparking.backend.identity.entity.enums.DocumentSide;
import com.dreamparking.backend.identity.entity.enums.OcrStatus;
import com.dreamparking.backend.identity.ocr.DocumentPhoto;
import com.dreamparking.backend.identity.ocr.DuiOcrClient;
import com.dreamparking.backend.identity.ocr.DuiOcrException;
import com.dreamparking.backend.identity.ocr.DuiReading;
import com.dreamparking.backend.identity.repository.IdentityDocumentImageRepository;
import com.dreamparking.backend.identity.repository.IdentityDocumentRepository;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.onboarding.service.OnboardingService;

/**
 * Identity document step (VDI-79, VDI-80): stores the DUI photos encrypted and reads their data. Unreadable photos
 * are kept (the last attempt) but do not complete the step; a provider failure does, so an outage never blocks the
 * request and the customer types the data instead. The provider call runs outside the database transaction so a slow
 * answer does not hold a connection of the small pool.
 */
@Service
public class IdentityDocumentService {

	private static final Logger log = LoggerFactory.getLogger(IdentityDocumentService.class);

	static final String JPEG = "image/jpeg";

	static final String PNG = "image/png";

	private final OnboardingService onboardingService;

	private final IdentityDocumentRepository documents;

	private final IdentityDocumentImageRepository images;

	private final DuiOcrClient ocr;

	private final DocumentCipher cipher;

	private final TransactionTemplate transactions;

	public IdentityDocumentService(OnboardingService onboardingService, IdentityDocumentRepository documents,
			IdentityDocumentImageRepository images, DuiOcrClient ocr, DocumentCipher cipher,
			TransactionTemplate transactions) {
		this.onboardingService = onboardingService;
		this.documents = documents;
		this.images = images;
		this.ocr = ocr;
		this.cipher = cipher;
		this.transactions = transactions;
	}

	/** A decrypted photo for the console. */
	public record Photo(String contentType, byte[] content) {
	}

	/**
	 * Saves the photos and what was read from them.
	 * @throws UnreadableDocumentException when the customer has to take the photos again; it is thrown after the
	 * transaction is committed, so the attempt stays on file
	 */
	public IdentityDocumentResponse capture(UUID requestId, byte[] front, byte[] back) {
		DocumentPhoto frontPhoto = photo("front", front);
		DocumentPhoto backPhoto = photo("back", back);
		transactions.executeWithoutResult(status -> onboardingService.findInProgress(requestId));

		DuiReading reading = null;
		String failure = null;
		try {
			reading = ocr.read(frontPhoto, backPhoto);
		}
		catch (DuiOcrException ex) {
			log.warn("The identity document of a request could not be read: {}", ex.getMessage());
			failure = ex.getMessage();
		}
		OcrStatus status = statusOf(reading);
		DuiReading read = reading;
		String failed = failure;
		IdentityDocument document = transactions.execute(tx -> save(requestId, frontPhoto, backPhoto, status, read, failed));
		if (document.getStatus() == OcrStatus.UNREADABLE) {
			throw new UnreadableDocumentException(document.getUnreadableReason());
		}
		return IdentityDocumentResponse.of(document);
	}

	private static OcrStatus statusOf(DuiReading reading) {
		if (reading == null) {
			return OcrStatus.FAILED;
		}
		return reading.isComplete() ? OcrStatus.READ : OcrStatus.UNREADABLE;
	}

	private IdentityDocument save(UUID requestId, DocumentPhoto front, DocumentPhoto back, OcrStatus status,
			DuiReading reading, String failure) {
		OnboardingRequest request = onboardingService.findInProgress(requestId);
		Instant now = Instant.now();

		store(request, DocumentSide.FRONT, front, now);
		store(request, DocumentSide.BACK, back, now);

		IdentityDocument document = documents.findById(requestId).orElseGet(() -> new IdentityDocument(request));
		document.recordReading(status, reading, failure, ocr.model(), now);
		if (request.getDui() != null) {
			// Captured again after the basic data: compare with what the customer already confirmed.
			document.confirm(request.getDui(), request.getFirstNames(), request.getLastNames());
		}
		documents.save(document);

		if (status != OcrStatus.UNREADABLE) {
			onboardingService.completeIdentityDocument(request, status);
		}
		request.setLastActivityAt(now);
		return document;
	}

	private void store(OnboardingRequest request, DocumentSide side, DocumentPhoto photo, Instant now) {
		byte[] content = photo.content();
		DocumentCipher.Sealed sealed = cipher.encrypt(content,
				IdentityDocumentImage.associatedData(request.getId(), side));
		IdentityDocumentImage image = images.findByRequestIdAndSide(request.getId(), side)
			.orElseGet(() -> new IdentityDocumentImage(request, side));
		image.store(photo.contentType(), sealed.iv(), sealed.ciphertext(), sealed.keyVersion(), sha256(content),
				content.length, now);
		images.save(image);
	}

	/** The decrypted photo of one side, for the console. */
	@Transactional(readOnly = true)
	public Photo photo(UUID requestId, DocumentSide side) {
		IdentityDocumentImage image = images.findByRequestIdAndSide(requestId, side)
			.orElseThrow(() -> new NotFoundException("Request " + requestId + " has no " + side + " photo of its DUI"));
		byte[] content = cipher.decrypt(image.getIv(), image.getCiphertext(), image.getKeyVersion(),
				IdentityDocumentImage.associatedData(requestId, side));
		return new Photo(image.getContentType(), content);
	}

	/** Checks the photo is a JPEG or PNG by its first bytes; the declared content type is not trusted. */
	static DocumentPhoto photo(String part, byte[] content) {
		if (content == null || content.length == 0) {
			throw new InvalidInputException("The " + part + " photo is empty");
		}
		String type = contentTypeOf(content);
		if (type == null) {
			throw new InvalidInputException("The " + part + " photo must be a JPEG or PNG image");
		}
		return new DocumentPhoto(content, type);
	}

	static String contentTypeOf(byte[] content) {
		if (content.length >= 3 && (content[0] & 0xff) == 0xff && (content[1] & 0xff) == 0xd8
				&& (content[2] & 0xff) == 0xff) {
			return JPEG;
		}
		if (content.length >= 8 && (content[0] & 0xff) == 0x89 && content[1] == 'P' && content[2] == 'N'
				&& content[3] == 'G' && content[4] == 0x0d && content[5] == 0x0a && content[6] == 0x1a
				&& content[7] == 0x0a) {
			return PNG;
		}
		return null;
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
