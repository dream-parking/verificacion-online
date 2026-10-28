package com.dreamparking.backend.risklist.service;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.risklist.dto.RiskListMatch;
import com.dreamparking.backend.risklist.entity.enums.RiskListDataType;
import com.dreamparking.backend.risklist.repository.RiskListEntryRepository;

/**
 * Crosses the data of a request against the active entries of the internal risk lists. The name of the person plays no
 * part: only the document, the phone and the device are looked up, so someone who comes back with another name is
 * still found. It does not feed the score yet (the matching engine does that).
 */
@Service
public class RiskListLookupService {

	private final RiskListEntryRepository entries;

	private final RiskListHasher hasher;

	public RiskListLookupService(RiskListEntryRepository entries, RiskListHasher hasher) {
		this.entries = entries;
		this.hasher = hasher;
	}

	/**
	 * The matches of the DUI, the mobile phone and the device fingerprint of a request, heaviest first. Any of them may
	 * be null or blank; one that cannot be a value of its type matches nothing. A value on several lists matches each.
	 */
	@Transactional(readOnly = true)
	public List<RiskListMatch> findMatches(String document, String phone, String deviceFingerprint) {
		Set<String> hashes = new LinkedHashSet<>();
		addHash(hashes, RiskListDataType.DOCUMENT, document);
		addHash(hashes, RiskListDataType.PHONE, phone);
		addHash(hashes, RiskListDataType.DEVICE, deviceFingerprint);
		if (hashes.isEmpty()) {
			return List.of();
		}
		return entries.findActiveByHashes(hashes)
			.stream()
			.map(RiskListMatch::of)
			.sorted(Comparator.comparingInt(RiskListMatch::weight)
				.reversed()
				.thenComparing(RiskListMatch::listCode)
				.thenComparing(RiskListMatch::entryId))
			.toList();
	}

	private void addHash(Set<String> hashes, RiskListDataType type, String value) {
		type.normalize(value).map(normalized -> hasher.hash(type, normalized)).ifPresent(hashes::add);
	}

}
