package com.dreamparking.backend.outcome.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.console.entity.ConsoleUser;
import com.dreamparking.backend.console.service.AccessAuditService;
import com.dreamparking.backend.console.service.ConsoleUserService;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.onboarding.repository.OnboardingRequestRepository;
import com.dreamparking.backend.outcome.dto.RegisterOutcomeRequest;
import com.dreamparking.backend.outcome.dto.RequestOutcomeResponse;
import com.dreamparking.backend.outcome.entity.RequestOutcome;
import com.dreamparking.backend.outcome.entity.RequestOutcomeEntry;
import com.dreamparking.backend.outcome.entity.enums.CaseOutcome;
import com.dreamparking.backend.outcome.entity.enums.OutcomeEntryLink;
import com.dreamparking.backend.outcome.repository.RequestOutcomeEntryRepository;
import com.dreamparking.backend.outcome.repository.RequestOutcomeRepository;
import com.dreamparking.backend.risklist.entity.RiskList;
import com.dreamparking.backend.risklist.entity.RiskListEntry;
import com.dreamparking.backend.risklist.entity.RiskListEntryChange;
import com.dreamparking.backend.risklist.entity.enums.RiskListChangeAction;
import com.dreamparking.backend.risklist.entity.enums.RiskListDataType;
import com.dreamparking.backend.risklist.repository.RiskListEntryChangeRepository;
import com.dreamparking.backend.risklist.repository.RiskListEntryRepository;
import com.dreamparking.backend.risklist.repository.RiskListRepository;
import com.dreamparking.backend.risklist.service.RiskListHasher;

/**
 * Registers how a case ended (VDI-144). A confirmed fraud puts the DUI, the mobile phone and the device of the request
 * on the {@code FRAUDE_CONFIRMADO} list, hashed with {@link RiskListHasher}; a false positive is only recorded. An
 * outcome is never edited: correcting it registers a new one, and a false positive takes off the entries that the
 * earlier outcomes of the request had created (VDI-142 deactivates them, it does not delete them). Registering the same
 * outcome again adds a row but no entries: what is already on the list stays as it is. Only what an outcome added is
 * taken off by a correction, never an entry that was on the list by other means.
 */
@Service
public class RequestOutcomeService {

	/** The seeded list (V37) that holds the data of confirmed frauds. */
	static final String FRAUD_LIST = "FRAUDE_CONFIRMADO";

	private final RequestOutcomeRepository outcomes;

	private final RequestOutcomeEntryRepository links;

	private final OnboardingRequestRepository requests;

	private final RiskListRepository lists;

	private final RiskListEntryRepository entries;

	private final RiskListEntryChangeRepository changes;

	private final RiskListHasher hasher;

	private final ConsoleUserService consoleUserService;

	private final AccessAuditService accessAuditService;

	public RequestOutcomeService(RequestOutcomeRepository outcomes, RequestOutcomeEntryRepository links,
			OnboardingRequestRepository requests, RiskListRepository lists, RiskListEntryRepository entries,
			RiskListEntryChangeRepository changes, RiskListHasher hasher, ConsoleUserService consoleUserService,
			AccessAuditService accessAuditService) {
		this.outcomes = outcomes;
		this.links = links;
		this.requests = requests;
		this.lists = lists;
		this.entries = entries;
		this.changes = changes;
		this.hasher = hasher;
		this.consoleUserService = consoleUserService;
		this.accessAuditService = accessAuditService;
	}

	/** Records the outcome of the request and updates the lists as it says. */
	@Transactional
	public RequestOutcomeResponse register(UUID requestId, RegisterOutcomeRequest body, UUID userId) {
		ConsoleUser user = consoleUserService.activeUser(userId);
		// Locked, so two outcomes of the same request (or the same one twice) are applied one after the other.
		OnboardingRequest request = requests.findByIdForUpdate(requestId)
			.orElseThrow(() -> new NotFoundException("No existe la solicitud " + requestId));
		RequestOutcome outcome = outcomes.saveAndFlush(new RequestOutcome(request, body.outcome(), body.reason(), user));

		List<RiskListDataType> revoked = body.outcome() == CaseOutcome.FALSO_POSITIVO ? revoke(request, outcome, user)
				: List.of();
		List<RiskListDataType> listed = body.outcome() == CaseOutcome.FRAUDE_CONFIRMADO ? list(request, outcome, user)
				: List.of();
		accessAuditService.record(user, "REGISTER_REQUEST_OUTCOME", "onboarding_request", requestId.toString(), null);
		return RequestOutcomeResponse.of(outcome, listed, revoked);
	}

	/** The outcomes of the request, the oldest first, each with what it did to the lists. */
	@Transactional(readOnly = true)
	public List<RequestOutcomeResponse> history(UUID requestId) {
		if (!requests.existsById(requestId)) {
			throw new NotFoundException("No existe la solicitud " + requestId);
		}
		Map<Long, Map<OutcomeEntryLink, List<RiskListDataType>>> byOutcome = links.findByRequestId(requestId)
			.stream()
			.collect(Collectors.groupingBy(link -> link.getOutcome().getId(),
					Collectors.groupingBy(RequestOutcomeEntry::getLink,
							() -> new EnumMap<>(OutcomeEntryLink.class),
							Collectors.mapping(link -> link.getEntry().getDataType(), Collectors.toList()))));
		return outcomes.findByRequestIdOrderByIdAsc(requestId).stream().map(outcome -> {
			Map<OutcomeEntryLink, List<RiskListDataType>> done = byOutcome.getOrDefault(outcome.getId(), Map.of());
			return RequestOutcomeResponse.of(outcome, sorted(done.get(OutcomeEntryLink.LISTED)),
					sorted(done.get(OutcomeEntryLink.REVOKED)));
		}).toList();
	}

	/** Puts the document, the phone and the device of the request on the fraud list, except what is already there. */
	private List<RiskListDataType> list(OnboardingRequest request, RequestOutcome outcome, ConsoleUser user) {
		RiskList fraudList = lists.findByCode(FRAUD_LIST)
			.orElseThrow(() -> new IllegalStateException("The risk list " + FRAUD_LIST + " is not seeded"));
		String reason = "Fraude confirmado en la solicitud " + label(request) + ": " + outcome.getReason();
		List<RiskListDataType> listed = new ArrayList<>();
		for (RiskListDataType type : RiskListDataType.values()) {
			String hash = type.normalize(valueOf(request, type)).map(value -> hasher.hash(type, value)).orElse(null);
			if (hash == null || entries.existsByListIdAndValueHashAndActiveTrue(fraudList.getId(), hash)) {
				continue;
			}
			RiskListEntry entry = entries.saveAndFlush(new RiskListEntry(fraudList, type, hash, reason, user));
			changes.save(new RiskListEntryChange(entry, RiskListChangeAction.ADDED, user, reason));
			links.save(new RequestOutcomeEntry(outcome, entry, OutcomeEntryLink.LISTED));
			listed.add(type);
		}
		return listed;
	}

	/** Takes off the entries that the earlier outcomes of the request created and that are still active. */
	private List<RiskListDataType> revoke(OnboardingRequest request, RequestOutcome outcome, ConsoleUser user) {
		String reason = "Desenlace corregido a " + outcome.getOutcome() + " en la solicitud " + label(request) + ": "
				+ outcome.getReason();
		List<RiskListDataType> revoked = new ArrayList<>();
		for (RiskListEntry entry : links.findActiveListedByRequestId(request.getId())) {
			entry.deactivate(user, reason, Instant.now());
			entries.saveAndFlush(entry);
			changes.save(new RiskListEntryChange(entry, RiskListChangeAction.DEACTIVATED, user, reason));
			links.save(new RequestOutcomeEntry(outcome, entry, OutcomeEntryLink.REVOKED));
			revoked.add(entry.getDataType());
		}
		return revoked;
	}

	/** The data of the request as it is now: what the request carries and, when it has none, what its customer has. */
	private static String valueOf(OnboardingRequest request, RiskListDataType type) {
		return switch (type) {
			case DOCUMENT -> request.getDui() != null || request.getCustomer() == null ? request.getDui()
					: request.getCustomer().getDui();
			case PHONE -> request.getMobilePhone() != null || request.getCustomer() == null ? request.getMobilePhone()
					: request.getCustomer().getMobilePhone();
			case DEVICE -> request.getDevice() == null ? null : request.getDevice().getFingerprint();
		};
	}

	private static String label(OnboardingRequest request) {
		return request.getNumber() != null ? request.getNumber() : request.getId().toString();
	}

	private static List<RiskListDataType> sorted(List<RiskListDataType> types) {
		return types == null ? List.of() : types.stream().sorted().toList();
	}

}
