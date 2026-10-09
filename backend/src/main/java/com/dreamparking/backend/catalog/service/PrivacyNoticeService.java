package com.dreamparking.backend.catalog.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.catalog.dto.PrivacyNoticeResponse;
import com.dreamparking.backend.catalog.dto.PublishPrivacyNoticeRequest;
import com.dreamparking.backend.catalog.entity.PrivacyNotice;
import com.dreamparking.backend.catalog.repository.PrivacyNoticeRepository;
import com.dreamparking.backend.common.exception.InvalidStateException;
import com.dreamparking.backend.console.entity.ConsoleUser;
import com.dreamparking.backend.console.service.AccessAuditService;
import com.dreamparking.backend.console.service.ConsoleUserService;

/** Versions of the privacy notice. Only one is in force; a consent always points to the version accepted. */
@Service
public class PrivacyNoticeService {

	private final PrivacyNoticeRepository notices;

	private final ConsoleUserService consoleUserService;

	private final AccessAuditService accessAuditService;

	public PrivacyNoticeService(PrivacyNoticeRepository notices, ConsoleUserService consoleUserService,
			AccessAuditService accessAuditService) {
		this.notices = notices;
		this.consoleUserService = consoleUserService;
		this.accessAuditService = accessAuditService;
	}

	/** Notice the customer is accepting right now. */
	@Transactional(readOnly = true)
	public PrivacyNotice current() {
		return notices.findFirstByValidToIsNullAndValidFromLessThanEqualOrderByValidFromDesc(Instant.now())
			.orElseThrow(() -> new InvalidStateException("There is no privacy notice in force"));
	}

	/** Every version, newest first. */
	@Transactional(readOnly = true)
	public List<PrivacyNoticeResponse> list() {
		return notices.findAllByOrderByValidFromDesc().stream().map(PrivacyNoticeResponse::of).toList();
	}

	/**
	 * Publishes a new version, in force from now on; the previous one stops being in force at the same instant.
	 * Consents already given keep pointing to the version they accepted.
	 */
	@Transactional
	public PrivacyNoticeResponse publish(PublishPrivacyNoticeRequest body, UUID adminId) {
		ConsoleUser admin = consoleUserService.activeUser(adminId);
		if (notices.existsByVersion(body.version())) {
			throw new InvalidStateException("The privacy notice version " + body.version() + " already exists");
		}
		Instant now = Instant.now();
		notices.findFirstByValidToIsNullAndValidFromLessThanEqualOrderByValidFromDesc(now)
			.ifPresent(previous -> previous.setValidTo(now));

		PrivacyNotice notice = new PrivacyNotice();
		notice.setVersion(body.version());
		notice.setTextHash(body.textHash().toLowerCase(Locale.ROOT));
		notice.setValidFrom(now);
		PrivacyNotice saved = notices.save(notice);

		accessAuditService.record(admin, "PRIVACY_NOTICE_PUBLISH", "privacy-notice", saved.getVersion(), null);
		return PrivacyNoticeResponse.of(saved);
	}

}
