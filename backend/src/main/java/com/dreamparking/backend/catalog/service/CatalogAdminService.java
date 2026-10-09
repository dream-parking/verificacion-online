package com.dreamparking.backend.catalog.service;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.catalog.dto.CatalogEntryResponse;
import com.dreamparking.backend.catalog.dto.CatalogKind;
import com.dreamparking.backend.catalog.dto.CreateCatalogEntryRequest;
import com.dreamparking.backend.catalog.dto.UpdateCatalogEntryRequest;
import com.dreamparking.backend.catalog.entity.CatalogEntry;
import com.dreamparking.backend.catalog.entity.IncomeRange;
import com.dreamparking.backend.catalog.entity.IncomeSource;
import com.dreamparking.backend.catalog.entity.MonthlyAmountRange;
import com.dreamparking.backend.catalog.entity.RangeCatalogEntry;
import com.dreamparking.backend.catalog.entity.TransactionType;
import com.dreamparking.backend.catalog.repository.IncomeRangeRepository;
import com.dreamparking.backend.catalog.repository.IncomeSourceRepository;
import com.dreamparking.backend.catalog.repository.MonthlyAmountRangeRepository;
import com.dreamparking.backend.catalog.repository.TransactionTypeRepository;
import com.dreamparking.backend.common.exception.InvalidInputException;
import com.dreamparking.backend.common.exception.InvalidStateException;
import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.console.entity.ConsoleUser;
import com.dreamparking.backend.console.service.AccessAuditService;
import com.dreamparking.backend.console.service.ConsoleUserService;
import com.dreamparking.backend.onboarding.repository.ExpectedActivityRepository;
import com.dreamparking.backend.onboarding.repository.IncomeDeclarationRepository;

/**
 * Maintenance of the onboarding catalogs by administrators. Entries are never deleted (requests reference them):
 * they are deactivated. Every change is written to the access audit.
 */
@Service
public class CatalogAdminService {

	private record Store<T extends CatalogEntry>(JpaRepository<T, String> repository, Supplier<T> factory) {
	}

	private final Map<CatalogKind, Store<? extends CatalogEntry>> stores = new EnumMap<>(CatalogKind.class);

	private final IncomeDeclarationRepository incomeDeclarations;

	private final ExpectedActivityRepository expectedActivities;

	private final ConsoleUserService consoleUserService;

	private final AccessAuditService accessAuditService;

	public CatalogAdminService(IncomeSourceRepository incomeSources, IncomeRangeRepository incomeRanges,
			MonthlyAmountRangeRepository monthlyAmountRanges, TransactionTypeRepository transactionTypes,
			IncomeDeclarationRepository incomeDeclarations, ExpectedActivityRepository expectedActivities,
			ConsoleUserService consoleUserService, AccessAuditService accessAuditService) {
		stores.put(CatalogKind.INCOME_SOURCES, new Store<>(incomeSources, IncomeSource::new));
		stores.put(CatalogKind.INCOME_RANGES, new Store<>(incomeRanges, IncomeRange::new));
		stores.put(CatalogKind.MONTHLY_AMOUNT_RANGES, new Store<>(monthlyAmountRanges, MonthlyAmountRange::new));
		stores.put(CatalogKind.TRANSACTION_TYPES, new Store<>(transactionTypes, TransactionType::new));
		this.incomeDeclarations = incomeDeclarations;
		this.expectedActivities = expectedActivities;
		this.consoleUserService = consoleUserService;
		this.accessAuditService = accessAuditService;
	}

	/** Every entry, active or not, in display order. */
	@Transactional(readOnly = true)
	public List<CatalogEntryResponse> list(CatalogKind kind) {
		return store(kind).repository()
			.findAll(Sort.by("sortOrder", "code"))
			.stream()
			.map(CatalogEntryResponse::of)
			.toList();
	}

	@Transactional
	public CatalogEntryResponse create(CatalogKind kind, CreateCatalogEntryRequest body, UUID adminId) {
		return create(kind, store(kind), body, consoleUserService.activeUser(adminId));
	}

	@Transactional
	public CatalogEntryResponse update(CatalogKind kind, String code, UpdateCatalogEntryRequest body, UUID adminId) {
		return update(kind, store(kind), code, body, consoleUserService.activeUser(adminId));
	}

	private <T extends CatalogEntry> CatalogEntryResponse create(CatalogKind kind, Store<T> store,
			CreateCatalogEntryRequest body, ConsoleUser admin) {
		if (store.repository().existsById(body.code())) {
			throw new InvalidStateException("The " + kind.slug() + " catalog already has the code " + body.code());
		}
		T entry = store.factory().get();
		entry.setCode(body.code());
		entry.setLabel(body.label().trim());
		entry.setSortOrder(body.sortOrder() != null ? body.sortOrder() : nextSortOrder(store));
		entry.setActive(body.active() == null || body.active());
		applyBounds(kind, entry, body.minUsd(), body.maxUsd());
		checkNoOverlap(store, entry);

		T saved = store.repository().save(entry);
		accessAuditService.record(admin, "CATALOG_CREATE", kind.slug(), saved.getCode(), null);
		return CatalogEntryResponse.of(saved);
	}

	private <T extends CatalogEntry> CatalogEntryResponse update(CatalogKind kind, Store<T> store, String code,
			UpdateCatalogEntryRequest body, ConsoleUser admin) {
		T entry = store.repository()
			.findById(code)
			.orElseThrow(() -> new NotFoundException("The " + kind.slug() + " catalog has no code " + code));

		if (entry instanceof RangeCatalogEntry range
				&& (!sameAmount(range.getMinUsd(), body.minUsd()) || !sameAmount(range.getMaxUsd(), body.maxUsd()))
				&& inUse(kind, code)) {
			throw new InvalidStateException("The bounds of " + code + " are already part of submitted files: "
					+ "create a new range and deactivate this one");
		}
		if (Boolean.TRUE.equals(entry.getActive()) && !body.active() && activeCount(store) <= 1) {
			throw new InvalidStateException("The " + kind.slug() + " catalog needs at least one active entry");
		}

		entry.setLabel(body.label().trim());
		entry.setSortOrder(body.sortOrder());
		entry.setActive(body.active());
		applyBounds(kind, entry, body.minUsd(), body.maxUsd());
		checkNoOverlap(store, entry);

		accessAuditService.record(admin, "CATALOG_UPDATE", kind.slug(), code, null);
		return CatalogEntryResponse.of(entry);
	}

	private Store<? extends CatalogEntry> store(CatalogKind kind) {
		return stores.get(kind);
	}

	private static void applyBounds(CatalogKind kind, CatalogEntry entry, BigDecimal min, BigDecimal max) {
		if (!(entry instanceof RangeCatalogEntry range)) {
			if (min != null || max != null) {
				throw new InvalidInputException("Entries of " + kind.slug() + " have no amount bounds");
			}
			return;
		}
		if (min == null && max == null) {
			throw new InvalidInputException("A range needs minUsd, maxUsd or both");
		}
		if (min != null && max != null && min.compareTo(max) >= 0) {
			throw new InvalidInputException("minUsd must be less than maxUsd");
		}
		range.setMinUsd(min);
		range.setMaxUsd(max);
	}

	/** Active ranges of a catalog cannot overlap, or a declared amount would fall in two of them. */
	private static <T extends CatalogEntry> void checkNoOverlap(Store<T> store, CatalogEntry entry) {
		if (!(entry instanceof RangeCatalogEntry range) || !Boolean.TRUE.equals(range.getActive())) {
			return;
		}
		for (T other : store.repository().findAll()) {
			if (other.getCode().equals(range.getCode()) || !Boolean.TRUE.equals(other.getActive())) {
				continue;
			}
			RangeCatalogEntry o = (RangeCatalogEntry) other;
			if (lowerThanOrEqual(range.getMinUsd(), o.getMaxUsd()) && lowerThanOrEqual(o.getMinUsd(), range.getMaxUsd())) {
				throw new InvalidStateException("The range " + range.getCode() + " overlaps " + o.getCode());
			}
		}
	}

	/** {@code min <= max} where a null minimum is minus infinity and a null maximum is plus infinity. */
	private static boolean lowerThanOrEqual(BigDecimal min, BigDecimal max) {
		return min == null || max == null || min.compareTo(max) <= 0;
	}

	private static boolean sameAmount(BigDecimal a, BigDecimal b) {
		return a == null ? b == null : b != null && a.compareTo(b) == 0;
	}

	private boolean inUse(CatalogKind kind, String code) {
		return switch (kind) {
			case INCOME_RANGES -> incomeDeclarations.existsByRange_Code(code);
			case MONTHLY_AMOUNT_RANGES -> expectedActivities.existsByMonthlyAmountRange_Code(code);
			default -> false;
		};
	}

	private static <T extends CatalogEntry> long activeCount(Store<T> store) {
		return store.repository().findAll().stream().filter(e -> Boolean.TRUE.equals(e.getActive())).count();
	}

	private static <T extends CatalogEntry> short nextSortOrder(Store<T> store) {
		return (short) (store.repository().findAll().stream().mapToInt(CatalogEntry::getSortOrder).max().orElse(0) + 1);
	}

}
