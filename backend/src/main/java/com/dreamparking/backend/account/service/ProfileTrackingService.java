package com.dreamparking.backend.account.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.account.dto.MovementResponse;
import com.dreamparking.backend.account.dto.ProfileComparisonResponse;
import com.dreamparking.backend.account.dto.ProfileComparisonResponse.Actual;
import com.dreamparking.backend.account.dto.ProfileComparisonResponse.Declared;
import com.dreamparking.backend.account.dto.ProfileComparisonResponse.Deviation;
import com.dreamparking.backend.account.dto.ProfileComparisonResponse.TypeTotal;
import com.dreamparking.backend.account.dto.RegisterMovementRequest;
import com.dreamparking.backend.account.dto.RegisteredMovementResponse;
import com.dreamparking.backend.account.entity.Account;
import com.dreamparking.backend.account.entity.AccountMovement;
import com.dreamparking.backend.account.entity.ProfileDeviation;
import com.dreamparking.backend.account.entity.ProfileDeviationId;
import com.dreamparking.backend.account.entity.enums.AccountStatus;
import com.dreamparking.backend.account.entity.enums.MovementDirection;
import com.dreamparking.backend.account.entity.enums.ProfileDeviationType;
import com.dreamparking.backend.account.repository.AccountMovementRepository;
import com.dreamparking.backend.account.repository.AccountRepository;
import com.dreamparking.backend.account.repository.ProfileDeviationRepository;
import com.dreamparking.backend.alert.dto.RaiseAlertRequest;
import com.dreamparking.backend.alert.service.AlertService;
import com.dreamparking.backend.catalog.entity.MonthlyAmountRange;
import com.dreamparking.backend.catalog.service.CatalogService;
import com.dreamparking.backend.common.exception.InvalidStateException;
import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.onboarding.entity.ExpectedActivity;
import com.dreamparking.backend.onboarding.repository.ExpectedActivityRepository;

/**
 * Tracking of the declared transactional profile (VDI-3). The core reports the movements of each account; they are
 * added up by calendar month (El Salvador) and compared with the expected activity declared in the onboarding request.
 * The first time a kind of deviation shows up in a month it is recorded and raises an alert for the console; later
 * movements of the same month do not raise it again. A figure counts as a deviation when it exceeds what was declared
 * by more than the tolerance in force ({@link ProfileToleranceService}, VDI-137), read again with every movement so a
 * change applies to the next one; each recorded deviation keeps the tolerance it was detected with.
 */
@Service
public class ProfileTrackingService {

	/** Months are calendar months in El Salvador, like the console shows the dates. */
	static final ZoneId LOCAL_ZONE = ZoneId.of("America/El_Salvador");

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private final AccountRepository accounts;

	private final AccountMovementRepository movements;

	private final ProfileDeviationRepository deviations;

	private final ExpectedActivityRepository expectedActivities;

	private final CatalogService catalogService;

	private final AlertService alertService;

	private final ProfileToleranceService toleranceService;

	public ProfileTrackingService(AccountRepository accounts, AccountMovementRepository movements,
			ProfileDeviationRepository deviations, ExpectedActivityRepository expectedActivities,
			CatalogService catalogService, AlertService alertService, ProfileToleranceService toleranceService) {
		this.accounts = accounts;
		this.movements = movements;
		this.deviations = deviations;
		this.expectedActivities = expectedActivities;
		this.catalogService = catalogService;
		this.alertService = alertService;
		this.toleranceService = toleranceService;
	}

	/**
	 * Records a movement reported by the core and compares its month with the declared profile. Returns the deviations
	 * that showed up for the first time with this movement (each one raised an alert).
	 */
	@Transactional
	public RegisteredMovementResponse register(UUID accountId, RegisterMovementRequest body) {
		Account account = accounts.lockById(accountId)
			.orElseThrow(() -> new NotFoundException("Account not found: " + accountId));
		if (account.getStatus() == AccountStatus.CLOSED) {
			throw new InvalidStateException("Account " + accountId + " is closed");
		}
		if (movements.existsByAccountIdAndExternalId(accountId, body.externalId())) {
			throw new InvalidStateException("Movement " + body.externalId() + " is already registered");
		}

		AccountMovement movement = new AccountMovement();
		movement.setAccount(account);
		movement.setExternalId(body.externalId());
		movement.setDirection(body.direction());
		movement.setAmountUsd(body.amountUsd());
		movement.setTransactionType(body.transactionTypeCode() == null || body.transactionTypeCode().isBlank() ? null
				: catalogService.activeTransactionType(body.transactionTypeCode()));
		movement.setDescription(body.description());
		movement.setOccurredAt(body.occurredAt());
		movements.saveAndFlush(movement);

		YearMonth month = YearMonth.from(body.occurredAt().atZone(LOCAL_ZONE));
		List<Deviation> raised = declaredProfile(account)
			.map(declared -> recordNewDeviations(account, declared, month))
			.orElse(List.of());
		return new RegisteredMovementResponse(MovementResponse.of(movement), raised);
	}

	/** Movements of the account in the month (the current one when null), oldest first. */
	@Transactional(readOnly = true)
	public List<MovementResponse> movements(UUID accountId, YearMonth month) {
		Account account = find(accountId);
		return movementsOf(account, month == null ? currentMonth() : month).stream().map(MovementResponse::of).toList();
	}

	/** The declared profile against the real activity of the month (the current one when null). */
	@Transactional(readOnly = true)
	public ProfileComparisonResponse compare(UUID accountId, YearMonth month) {
		Account account = find(accountId);
		ExpectedActivity declared = declaredProfile(account).orElseThrow(() -> new InvalidStateException(
				"Account " + accountId + " has no declared profile: it was not opened from an onboarding request"));
		YearMonth period = month == null ? currentMonth() : month;

		Map<ProfileDeviationType, ProfileDeviation> recorded = deviations
			.findByIdAccountIdAndIdPeriod(accountId, period.atDay(1))
			.stream()
			.collect(Collectors.toMap(d -> d.getId().getDeviation(), Function.identity(), (a, b) -> a,
					() -> new EnumMap<>(ProfileDeviationType.class)));
		int tolerancePercent = toleranceService.current();
		Evaluation evaluation = evaluate(declared, movementsOf(account, period), period, tolerancePercent);
		List<Deviation> found = new ArrayList<>();
		for (Finding finding : evaluation.findings()) {
			found.add(finding.toDeviation(recorded.get(finding.type())));
			recorded.remove(finding.type());
		}
		// A deviation already recorded stays even when the tolerance in force now allows it: it raised an alert.
		recorded.values().stream().map(ProfileTrackingService::toDeviation).forEach(found::add);
		return new ProfileComparisonResponse(accountId, declared.getRequestId(), period.toString(), tolerancePercent,
				toDeclared(declared), evaluation.actual(), found, found.isEmpty());
	}

	private List<Deviation> recordNewDeviations(Account account, ExpectedActivity declared, YearMonth month) {
		Set<ProfileDeviationType> alreadyRecorded = deviations
			.findByIdAccountIdAndIdPeriod(account.getId(), month.atDay(1))
			.stream()
			.map(d -> d.getId().getDeviation())
			.collect(Collectors.toSet());

		int tolerancePercent = toleranceService.current();
		List<Deviation> raised = new ArrayList<>();
		for (Finding finding : evaluate(declared, movementsOf(account, month), month, tolerancePercent).findings()) {
			if (alreadyRecorded.contains(finding.type())) {
				continue;
			}
			Map<String, Object> evidence = new LinkedHashMap<>();
			evidence.put("source", "PROFILE_TRACKING");
			evidence.put("month", month.toString());
			evidence.put("deviation", finding.type().name());
			evidence.put("declared", finding.declared());
			evidence.put("actual", finding.actual());
			evidence.put("limit", finding.limit());
			evidence.put("tolerancePercent", tolerancePercent);
			UUID alertId = alertService
				.raise(new RaiseAlertRequest(account.getId(), finding.type().alertTypeCode(), null, finding.detail(),
						evidence))
				.id();
			ProfileDeviation deviation = deviations.saveAndFlush(new ProfileDeviation(
					new ProfileDeviationId(account.getId(), month.atDay(1), finding.type()), finding.declared(),
					finding.actual(), finding.detail(), alertId, tolerancePercent));
			raised.add(finding.toDeviation(deviation));
		}
		return raised;
	}

	/** Adds up the month and finds every figure above what was declared (plus the tolerance). */
	private Evaluation evaluate(ExpectedActivity declared, List<AccountMovement> monthMovements, YearMonth month,
			int tolerancePercent) {
		BigDecimal inflow = BigDecimal.ZERO;
		BigDecimal outflow = BigDecimal.ZERO;
		int inflowCount = 0;
		int outflowCount = 0;
		Map<String, BigDecimal> amountByType = new LinkedHashMap<>();
		Map<String, Integer> countByType = new LinkedHashMap<>();
		for (AccountMovement movement : monthMovements) {
			if (movement.getDirection() == MovementDirection.OUT) {
				outflow = outflow.add(movement.getAmountUsd());
				outflowCount++;
				continue;
			}
			inflow = inflow.add(movement.getAmountUsd());
			inflowCount++;
			String type = movement.getTransactionType() == null ? null : movement.getTransactionType().getCode();
			amountByType.merge(type, movement.getAmountUsd(), BigDecimal::add);
			countByType.merge(type, 1, Integer::sum);
		}

		Set<String> declaredTypes = declared.declaredTransactionTypeCodes();
		List<TypeTotal> byType = amountByType.entrySet()
			.stream()
			.map(e -> new TypeTotal(e.getKey(), e.getValue(), countByType.get(e.getKey()),
					e.getKey() == null || declaredTypes.contains(e.getKey())))
			.toList();
		Actual actual = new Actual(inflow, inflowCount, outflow, outflowCount, byType);

		List<Finding> findings = new ArrayList<>();
		MonthlyAmountRange range = declared.getMonthlyAmountRange();
		if (range.getMaxUsd() != null && exceeds(inflow, range.getMaxUsd(), tolerancePercent)) {
			findings.add(new Finding(ProfileDeviationType.INFLOW_ABOVE_RANGE, range.getMaxUsd(), inflow,
					limit(range.getMaxUsd(), tolerancePercent), tolerancePercent, "Recibió USD %s en %s; el rango declarado «%s» llega hasta USD %s"
						.formatted(usd(inflow), month, range.getLabel(), usd(range.getMaxUsd()))));
		}
		if (declared.getMonthlyAmountUsd() != null
				&& exceeds(inflow, declared.getMonthlyAmountUsd(), tolerancePercent)) {
			findings.add(new Finding(ProfileDeviationType.INFLOW_ABOVE_DECLARED_AMOUNT,
					declared.getMonthlyAmountUsd(), inflow, limit(declared.getMonthlyAmountUsd(), tolerancePercent),
					tolerancePercent,
					"Recibió USD %s en %s; declaró que recibiría unos USD %s al mes"
						.formatted(usd(inflow), month, usd(declared.getMonthlyAmountUsd()))));
		}
		if (declared.getMonthlyTransactionCount() != null) {
			BigDecimal expected = BigDecimal.valueOf(declared.getMonthlyTransactionCount());
			BigDecimal received = BigDecimal.valueOf(inflowCount);
			if (exceeds(received, expected, tolerancePercent)) {
				findings.add(new Finding(ProfileDeviationType.MORE_TRANSACTIONS_THAN_DECLARED, expected, received,
						limit(expected, tolerancePercent), tolerancePercent, "Recibió %d depósitos o pagos en %s; declaró %d al mes"
							.formatted(inflowCount, month, declared.getMonthlyTransactionCount())));
			}
		}
		if (declared.getMonthlyOutflowUsd() != null
				&& exceeds(outflow, declared.getMonthlyOutflowUsd(), tolerancePercent)) {
			findings.add(new Finding(ProfileDeviationType.OUTFLOW_ABOVE_DECLARED, declared.getMonthlyOutflowUsd(),
					outflow, limit(declared.getMonthlyOutflowUsd(), tolerancePercent), tolerancePercent, "Salieron USD %s en %s; declaró salidas de USD %s al mes"
						.formatted(usd(outflow), month, usd(declared.getMonthlyOutflowUsd()))));
		}
		List<TypeTotal> undeclared = byType.stream().filter(t -> !t.declared()).toList();
		if (!undeclared.isEmpty()) {
			BigDecimal amount = undeclared.stream().map(TypeTotal::amountUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
			String types = undeclared.stream().map(TypeTotal::transactionTypeCode).collect(Collectors.joining(", "));
			findings.add(new Finding(ProfileDeviationType.UNDECLARED_TRANSACTION_TYPE, null, amount, null,
					tolerancePercent,
					"Recibió USD %s en %s de tipos no declarados (%s); declaró %s"
						.formatted(usd(amount), month, types, String.join(", ", declaredTypes))));
		}
		return new Evaluation(actual, findings);
	}

	/** True when {@code actual} is above {@code declared} by more than the tolerance. */
	private static boolean exceeds(BigDecimal actual, BigDecimal declared, int tolerancePercent) {
		return actual.compareTo(limit(declared, tolerancePercent)) > 0;
	}

	private static BigDecimal limit(BigDecimal declared, int tolerancePercent) {
		return declared.multiply(HUNDRED.add(BigDecimal.valueOf(tolerancePercent)))
			.divide(HUNDRED, 2, RoundingMode.HALF_UP);
	}

	private static String usd(BigDecimal amount) {
		return String.format(Locale.US, "%,.2f", amount);
	}

	private Optional<ExpectedActivity> declaredProfile(Account account) {
		return account.getRequest() == null ? Optional.empty()
				: expectedActivities.findById(account.getRequest().getId());
	}

	private List<AccountMovement> movementsOf(Account account, YearMonth month) {
		Instant from = month.atDay(1).atStartOfDay(LOCAL_ZONE).toInstant();
		Instant to = month.plusMonths(1).atDay(1).atStartOfDay(LOCAL_ZONE).toInstant();
		return movements.findByAccountIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAt(
				account.getId(), from, to);
	}

	private Account find(UUID accountId) {
		return accounts.findById(accountId).orElseThrow(() -> new NotFoundException("Account not found: " + accountId));
	}

	private static YearMonth currentMonth() {
		return YearMonth.now(LOCAL_ZONE);
	}

	private static Declared toDeclared(ExpectedActivity declared) {
		MonthlyAmountRange range = declared.getMonthlyAmountRange();
		return new Declared(List.copyOf(declared.declaredTransactionTypeCodes()), range.getCode(), range.getLabel(),
				range.getMinUsd(), range.getMaxUsd(), declared.getMonthlyAmountUsd(),
				declared.getMonthlyTransactionCount(), declared.getMonthlyOutflowUsd());
	}

	private record Evaluation(Actual actual, List<Finding> findings) {
	}

	private record Finding(ProfileDeviationType type, BigDecimal declared, BigDecimal actual, BigDecimal limit,
			int tolerancePercent, String detail) {

		/** The finding; when it was already recorded, with the tolerance (and so the limit) it was detected with. */
		Deviation toDeviation(ProfileDeviation recorded) {
			if (recorded == null) {
				return new Deviation(type, type.alertTypeCode(), declared, actual, limit, detail, null, null,
						tolerancePercent);
			}
			return new Deviation(type, type.alertTypeCode(), declared, actual, recordedLimit(recorded), detail,
					recorded.getAlertId(), recorded.getDetectedAt(), recorded.getTolerancePercent());
		}

	}

	/** A recorded deviation the activity of the month no longer shows with the tolerance in force. */
	private static Deviation toDeviation(ProfileDeviation recorded) {
		ProfileDeviationType type = recorded.getId().getDeviation();
		return new Deviation(type, type.alertTypeCode(), recorded.getDeclaredValue(), recorded.getActualValue(),
				recordedLimit(recorded), recorded.getDetail(), recorded.getAlertId(), recorded.getDetectedAt(),
				recorded.getTolerancePercent());
	}

	private static BigDecimal recordedLimit(ProfileDeviation recorded) {
		return recorded.getDeclaredValue() == null ? null
				: limit(recorded.getDeclaredValue(), recorded.getTolerancePercent());
	}

}
