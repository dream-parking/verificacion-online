package com.dreamparking.backend.catalog.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.catalog.entity.PrivacyNotice;
import com.dreamparking.backend.catalog.repository.PrivacyNoticeRepository;
import com.dreamparking.backend.common.exception.InvalidStateException;

@Service
public class PrivacyNoticeService {

	private final PrivacyNoticeRepository notices;

	public PrivacyNoticeService(PrivacyNoticeRepository notices) {
		this.notices = notices;
	}

	/** Notice the customer is accepting right now. */
	@Transactional(readOnly = true)
	public PrivacyNotice current() {
		return notices.findFirstByValidToIsNullAndValidFromLessThanEqualOrderByValidFromDesc(Instant.now())
			.orElseThrow(() -> new InvalidStateException("There is no privacy notice in force"));
	}

}
