package com.dreamparking.backend.risklist.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.common.exception.InvalidInputException;
import com.dreamparking.backend.common.exception.InvalidStateException;
import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.console.entity.ConsoleUser;
import com.dreamparking.backend.console.service.AccessAuditService;
import com.dreamparking.backend.console.service.ConsoleUserService;
import com.dreamparking.backend.risklist.dto.CreateRiskListEntryRequest;
import com.dreamparking.backend.risklist.dto.DeactivateRiskListEntryRequest;
import com.dreamparking.backend.risklist.dto.RiskListEntryChangeResponse;
import com.dreamparking.backend.risklist.dto.RiskListEntryResponse;
import com.dreamparking.backend.risklist.dto.RiskListResponse;
import com.dreamparking.backend.risklist.entity.RiskList;
import com.dreamparking.backend.risklist.entity.RiskListEntry;
import com.dreamparking.backend.risklist.entity.RiskListEntryChange;
import com.dreamparking.backend.risklist.entity.enums.RiskListChangeAction;
import com.dreamparking.backend.risklist.repository.RiskListEntryChangeRepository;
import com.dreamparking.backend.risklist.repository.RiskListEntryRepository;
import com.dreamparking.backend.risklist.repository.RiskListRepository;

/**
 * Administration of the internal risk lists: adding data to a list and taking it off. The value is hashed before it
 * reaches the database and is never logged, audited or returned. Taking an entry off marks it inactive, and every change
 * is logged with who, when and why.
 */
@Service
public class RiskListAdminService {

	private final RiskListRepository lists;

	private final RiskListEntryRepository entries;

	private final RiskListEntryChangeRepository changes;

	private final RiskListHasher hasher;

	private final ConsoleUserService consoleUserService;

	private final AccessAuditService accessAuditService;

	public RiskListAdminService(RiskListRepository lists, RiskListEntryRepository entries,
			RiskListEntryChangeRepository changes, RiskListHasher hasher, ConsoleUserService consoleUserService,
			AccessAuditService accessAuditService) {
		this.lists = lists;
		this.entries = entries;
		this.changes = changes;
		this.hasher = hasher;
		this.consoleUserService = consoleUserService;
		this.accessAuditService = accessAuditService;
	}

	/** The lists, heaviest first. */
	@Transactional(readOnly = true)
	public List<RiskListResponse> lists() {
		return lists.findAllByOrderByWeightDescCodeAsc().stream().map(RiskListResponse::of).toList();
	}

	/** Every entry of a list, active or not, newest first. */
	@Transactional(readOnly = true)
	public List<RiskListEntryResponse> entries(String listCode) {
		list(listCode);
		return entries.findByListCodeOrderByIdDesc(listCode).stream().map(RiskListEntryResponse::of).toList();
	}

	/** Puts the value on the list. */
	@Transactional
	public RiskListEntryResponse add(String listCode, CreateRiskListEntryRequest body, UUID adminId) {
		ConsoleUser admin = consoleUserService.activeUser(adminId);
		RiskList list = list(listCode);
		String normalized = body.dataType()
			.normalize(body.value())
			.orElseThrow(() -> new InvalidInputException(
					"The value is not a valid " + body.dataType().name().toLowerCase() + " (" + body.dataType().label() + ")"));
		String hash = hasher.hash(body.dataType(), normalized);
		if (entries.existsByListIdAndValueHashAndActiveTrue(list.getId(), hash)) {
			throw new InvalidStateException("That value is already active on the list " + listCode);
		}

		RiskListEntry entry = new RiskListEntry(list, body.dataType(), hash, body.reason(), admin);
		try {
			// Flush now: a concurrent duplicate trips the unique index here, not at commit.
			entries.saveAndFlush(entry);
		}
		catch (DataIntegrityViolationException ex) {
			throw new InvalidStateException("That value is already active on the list " + listCode);
		}
		changes.save(new RiskListEntryChange(entry, RiskListChangeAction.ADDED, admin, body.reason()));
		accessAuditService.record(admin, "RISK_LIST_ENTRY_ADD", "risk-list-entry", String.valueOf(entry.getId()), null);
		return RiskListEntryResponse.of(entry);
	}

	/** Takes the entry off: it stops matching, and stays in the file and in its history. */
	@Transactional
	public RiskListEntryResponse deactivate(Long entryId, DeactivateRiskListEntryRequest body, UUID adminId) {
		ConsoleUser admin = consoleUserService.activeUser(adminId);
		RiskListEntry entry = entry(entryId);
		if (!entry.isActive()) {
			throw new InvalidStateException("The risk list entry " + entryId + " is already inactive");
		}
		entry.deactivate(admin, body.reason(), Instant.now());
		entries.saveAndFlush(entry);
		changes.save(new RiskListEntryChange(entry, RiskListChangeAction.DEACTIVATED, admin, body.reason()));
		accessAuditService.record(admin, "RISK_LIST_ENTRY_REMOVE", "risk-list-entry", String.valueOf(entryId), null);
		return RiskListEntryResponse.of(entry);
	}

	/** The history of an entry, oldest first. */
	@Transactional(readOnly = true)
	public List<RiskListEntryChangeResponse> history(Long entryId) {
		entry(entryId);
		return changes.findByEntryIdOrderByIdAsc(entryId).stream().map(RiskListEntryChangeResponse::of).toList();
	}

	private RiskList list(String code) {
		return lists.findByCode(code).orElseThrow(() -> new NotFoundException("Risk list not found: " + code));
	}

	private RiskListEntry entry(Long id) {
		return entries.findById(id).orElseThrow(() -> new NotFoundException("Risk list entry not found: " + id));
	}

}
