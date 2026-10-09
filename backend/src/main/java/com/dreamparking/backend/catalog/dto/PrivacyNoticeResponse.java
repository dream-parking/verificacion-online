package com.dreamparking.backend.catalog.dto;

import java.time.Instant;

import com.dreamparking.backend.catalog.entity.PrivacyNotice;

/** Version of the privacy notice; {@code validTo} is null for the one in force. */
public record PrivacyNoticeResponse(short id, String version, String textHash, Instant validFrom, Instant validTo) {

	public static PrivacyNoticeResponse of(PrivacyNotice notice) {
		return new PrivacyNoticeResponse(notice.getId(), notice.getVersion(), notice.getTextHash(),
				notice.getValidFrom(), notice.getValidTo());
	}

}
