package com.dreamparking.backend.console.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.common.dto.PageResponse;
import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.console.dto.ConsoleRequestDetail;
import com.dreamparking.backend.console.dto.ConsoleRequestListItem;
import com.dreamparking.backend.console.dto.ConsoleRequestLocation;
import com.dreamparking.backend.identity.entity.IdentityDocument;
import com.dreamparking.backend.identity.entity.enums.OcrStatus;
import com.dreamparking.backend.identity.repository.IdentityDocumentImageRepository;
import com.dreamparking.backend.identity.repository.IdentityDocumentRepository;
import com.dreamparking.backend.identity.service.DuiValidator;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.onboarding.entity.RequestSignals;
import com.dreamparking.backend.onboarding.entity.enums.LocationStatus;
import com.dreamparking.backend.onboarding.entity.enums.RequestStatus;
import com.dreamparking.backend.onboarding.entity.ExpectedActivityVersion;
import com.dreamparking.backend.onboarding.repository.ExpectedActivityRepository;
import com.dreamparking.backend.onboarding.repository.ExpectedActivityVersionRepository;
import com.dreamparking.backend.onboarding.repository.IncomeDeclarationRepository;
import com.dreamparking.backend.onboarding.repository.OnboardingRequestRepository;
import com.dreamparking.backend.onboarding.repository.PrivacyConsentRepository;
import com.dreamparking.backend.onboarding.repository.RequestEventRepository;
import com.dreamparking.backend.onboarding.repository.RequestSignalsRepository;
import com.dreamparking.backend.onboarding.repository.RequestStepRepository;
import com.dreamparking.backend.risk.entity.enums.RiskLevel;
import com.dreamparking.backend.risk.service.RiskAssessmentService;
import com.dreamparking.backend.risk.service.RiskDecisionService;

/** Read side of the console: request list and detail. */
@Service
@Transactional(readOnly = true)
public class ConsoleRequestService {

	static final int MAX_PAGE_SIZE = 100;

	/** Dates printed on the DUI are Salvadoran dates. */
	static final ZoneId EL_SALVADOR = ZoneId.of("America/El_Salvador");

	/** Console filters; a null parameter disables its filter. Fixed SQL: the values only travel as bind parameters. */
	private static final String LIST_FILTER = """
			WHERE (CAST(:status AS text) IS NULL OR status = CAST(:status AS request_status))
			  AND (CAST(:riskLevel AS text) IS NULL OR risk_level = CAST(:riskLevel AS risk_level))
			  AND (CAST(:like AS text) IS NULL
			       OR lower(name) LIKE CAST(:like AS text) ESCAPE '\\'
			       OR lower(number) LIKE CAST(:like AS text) ESCAPE '\\')
			""";

	private static final String LIST_PAGE = """
			SELECT v.*, p.total FROM (
			  SELECT id, activity_date, count(*) OVER () AS total FROM v_request_list
			""" + LIST_FILTER + """
			  ORDER BY activity_date DESC, id OFFSET :offset LIMIT :limit
			) p JOIN v_request_list v ON v.id = p.id
			ORDER BY p.activity_date DESC, p.id""";

	private static final String LIST_COUNT = "SELECT count(*) FROM v_request_list " + LIST_FILTER;

	/** Same order as the list; the location is the one of the last session, as in the detail. */
	private static final String LOCATIONS = """
			SELECT l.id, l.number, l.name, l.risk_level, l.status, host(s.ip) AS ip, s.location_status,
			       s.geo_latitude, s.geo_longitude, s.geo_city, s.geo_region_name, s.geo_country, s.approximate_location
			FROM v_request_list l
			LEFT JOIN LATERAL (SELECT x.* FROM onboarding_session x WHERE x.request_id = l.id
			                   ORDER BY x.started_at DESC LIMIT 1) s ON true
			ORDER BY l.activity_date DESC, l.id""";

	private final NamedParameterJdbcTemplate jdbc;

	private final OnboardingRequestRepository requests;

	private final IncomeDeclarationRepository incomeDeclarations;

	private final ExpectedActivityRepository expectedActivities;

	private final RequestSignalsRepository signals;

	private final RequestStepRepository steps;

	private final RequestEventRepository events;

	private final RiskAssessmentService riskAssessments;

	private final RiskDecisionService riskDecisions;

	private final IdentityDocumentRepository identityDocuments;

	private final IdentityDocumentImageRepository identityDocumentImages;

	private final ExpectedActivityVersionRepository expectedActivityVersions;

	private final PrivacyConsentRepository consents;

	public ConsoleRequestService(NamedParameterJdbcTemplate jdbc, OnboardingRequestRepository requests,
			IncomeDeclarationRepository incomeDeclarations, ExpectedActivityRepository expectedActivities,
			RequestSignalsRepository signals, RequestStepRepository steps, RequestEventRepository events,
			RiskAssessmentService riskAssessments, RiskDecisionService riskDecisions,
			IdentityDocumentRepository identityDocuments, IdentityDocumentImageRepository identityDocumentImages,
			ExpectedActivityVersionRepository expectedActivityVersions, PrivacyConsentRepository consents) {
		this.jdbc = jdbc;
		this.requests = requests;
		this.incomeDeclarations = incomeDeclarations;
		this.expectedActivities = expectedActivities;
		this.signals = signals;
		this.steps = steps;
		this.events = events;
		this.riskAssessments = riskAssessments;
		this.riskDecisions = riskDecisions;
		this.identityDocuments = identityDocuments;
		this.identityDocumentImages = identityDocumentImages;
		this.expectedActivityVersions = expectedActivityVersions;
		this.consents = consents;
	}

	/**
	 * Newest first; {@code query} matches the applicant's name or the request number.
	 * <p>
	 * The console reads every page at once, so each page must be cheap: the inner query asks the view for the id and
	 * date only, PostgreSQL then drops the view's joins and walks {@code ix_request_activity} alone (index-only, also
	 * for deep pages), and the joins run for the rows of the page only. The total comes in the same round trip. Plain
	 * JDBC because the list is read-only: loading it as Hibernate entities cost about a third more CPU per page.
	 */
	public PageResponse<ConsoleRequestListItem> list(RequestStatus status, RiskLevel riskLevel, String query,
			int page, int size) {
		int pageNumber = Math.max(page, 0);
		int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		String like = query == null || query.isBlank() ? null
				: "%" + escapeLike(query.trim().toLowerCase()) + "%";
		MapSqlParameterSource params = new MapSqlParameterSource()
			.addValue("status", status == null ? null : status.name())
			.addValue("riskLevel", riskLevel == null ? null : riskLevel.name())
			.addValue("like", like)
			.addValue("offset", (long) pageNumber * pageSize)
			.addValue("limit", pageSize);

		long[] total = { -1 };
		List<ConsoleRequestListItem> content = jdbc.query(LIST_PAGE, params, (rs, row) -> {
			total[0] = rs.getLong("total");
			OffsetDateTime date = rs.getObject("activity_date", OffsetDateTime.class);
			int score = rs.getInt("score");
			return new ConsoleRequestListItem(rs.getObject("id", UUID.class), rs.getString("number"),
					rs.getString("name"), date == null ? null : date.toInstant(), rs.getString("transaction_type_label"),
					rs.getString("monthly_amount_range_label"), RiskLevel.valueOf(rs.getString("risk_level")),
					rs.wasNull() ? null : score, RequestStatus.valueOf(rs.getString("status")),
					rs.getShort("completed_steps"));
		});
		if (total[0] < 0) {
			// Past the last page (or nothing matches): the window count came with no row.
			total[0] = jdbc.queryForObject(LIST_COUNT, params, Long.class);
		}
		int totalPages = (int) ((total[0] + pageSize - 1) / pageSize);
		return new PageResponse<>(content, pageNumber, pageSize, total[0], totalPages);
	}

	/**
	 * Location of every request, newest first, for the console map. One query instead of a detail per request: the
	 * detail carries the DUI data and every read of it is audited.
	 */
	public List<ConsoleRequestLocation> locations() {
		return jdbc.query(LOCATIONS, (rs, row) -> {
			String locationStatus = rs.getString("location_status");
			return new ConsoleRequestLocation(rs.getObject("id", UUID.class), rs.getString("number"),
					rs.getString("name"), RiskLevel.valueOf(rs.getString("risk_level")),
					RequestStatus.valueOf(rs.getString("status")), rs.getString("ip"),
					locationStatus == null ? null : LocationStatus.valueOf(locationStatus),
					rs.getBigDecimal("geo_latitude"), rs.getBigDecimal("geo_longitude"), rs.getString("geo_city"),
					rs.getString("geo_region_name"), rs.getString("geo_country"), rs.getString("approximate_location"));
		});
	}

	public ConsoleRequestDetail detail(UUID requestId) {
		OnboardingRequest request = requests.findById(requestId)
			.orElseThrow(() -> new NotFoundException("Onboarding request not found: " + requestId));

		var applicant = new ConsoleRequestDetail.Applicant(request.getFirstNames(), request.getLastNames(),
				request.getDui(), request.getMobilePhone());
		var consent = consents.findById(requestId)
			.map(c -> new ConsoleRequestDetail.Consent(c.getNotice().getVersion(), c.getAcceptedAt(),
					Boolean.TRUE.equals(c.getSignalsAccepted()), c.getTermsAccepted()))
			.orElse(null);
		var identityDocument = identityDocuments.findById(requestId).map(this::toIdentityDocument).orElse(null);
		var income = incomeDeclarations.findById(requestId)
			.map(d -> new ConsoleRequestDetail.Income(d.getSource().getCode(), d.getSource().getLabel(),
					d.getSourceDetail(), d.getRange().getCode(), d.getRange().getLabel(), d.getRegisteredAt(),
					d.getUpdatedAt()))
			.orElse(null);
		var expectedActivity = expectedActivities.findById(requestId)
			.map(a -> new ConsoleRequestDetail.ExpectedActivity(a.getTransactionType().getCode(),
					a.getTransactionType().getLabel(), a.getMonthlyAmountRange().getCode(),
					a.getMonthlyAmountRange().getLabel(), List.copyOf(a.getExtraTransactionTypeCodes()),
					a.getMonthlyAmountUsd(), a.getMonthlyTransactionCount(), a.getMonthlyOutflowUsd(),
					a.getRegisteredAt(), a.getUpdatedAt(), profileVersions(requestId)))
			.orElse(null);
		var risk = riskAssessments.findCurrent(requestId).orElse(null);
		var signalsView = signals.findById(requestId)
			.filter(s -> s.getIp() != null || s.getDeviceFingerprint() != null)
			.map(ConsoleRequestService::toSignals)
			.orElse(null);
		var stepTimes = steps.findByRequestIdOrderByStartedAt(requestId)
			.stream()
			.map(s -> new ConsoleRequestDetail.StepTime(s.getStep(), s.getStartedAt(), s.getCompletedAt(),
					s.getDurationSeconds(), s.getAttempts(), s.getTypingSpeedCps()))
			.toList();
		var timeline = events.findByRequestIdOrderByOccurredAt(requestId)
			.stream()
			.map(e -> new ConsoleRequestDetail.TimelineEntry(e.getType(), e.getDescription(), e.getActor(),
					e.getOccurredAt()))
			.toList();

		return new ConsoleRequestDetail(request.getId(), request.getNumber(), request.getStatus(),
				request.getCompletedSteps(), request.getRiskLevel(), request.getStartedAt(),
				request.getSubmittedAt(), applicant, consent, identityDocument, income, expectedActivity, risk,
				riskDecisions.log(requestId), signalsView, stepTimes, timeline);
	}

	/** Every version of the declared profile, the oldest first; the last one is the one in force. */
	private List<ConsoleRequestDetail.ProfileVersion> profileVersions(UUID requestId) {
		List<ExpectedActivityVersion> versions = expectedActivityVersions.findByRequest_IdOrderByVersionAsc(requestId);
		return versions.stream()
			.map(v -> new ConsoleRequestDetail.ProfileVersion(v.getVersion(), v == versions.get(versions.size() - 1),
					v.getTransactionType().getCode(), v.getTransactionType().getLabel(),
					v.getMonthlyAmountRange().getCode(), v.getMonthlyAmountRange().getLabel(), v.getExtraTypeCodes(),
					v.getMonthlyAmountUsd(), v.getMonthlyTransactionCount(), v.getMonthlyOutflowUsd(),
					v.getChangedBy(), v.getDeclaredAt()))
			.toList();
	}

	private ConsoleRequestDetail.IdentityDocument toIdentityDocument(IdentityDocument d) {
		var read = d.getStatus() == OcrStatus.READ ? new ConsoleRequestDetail.DuiData(d.getDui(), d.getFirstNames(),
				d.getLastNames(), d.getBirthDate(), d.getIssueDate(), d.getExpiryDate(), d.getGender()) : null;
		var confirmed = d.isConfirmed() ? new ConsoleRequestDetail.DuiData(d.getConfirmedDui(),
				d.getConfirmedFirstNames(), d.getConfirmedLastNames(), d.getConfirmedBirthDate(), null,
				d.getConfirmedExpiryDate(), null) : null;
		String dui = d.isConfirmed() ? d.getConfirmedDui() : d.getDui();
		return new ConsoleRequestDetail.IdentityDocument(d.getStatus(), d.getModel(), d.getAttempts(),
				d.getUnreadableReason(), d.getUnreadableSide(), d.getFailure(), read, confirmed, d.getConfirmedAt(),
				d.getCorrectedFields(), d.getDuiMatchesDeclared(), d.getNamesMatchDeclared(),
				dui == null ? null : DuiValidator.hasValidCheckDigit(dui),
				d.effectiveExpiryDate() == null ? null : d.isExpired(LocalDate.now(EL_SALVADOR)), d.getLooksAuthentic(),
				d.getConfidence(), identityDocumentImages.findSidesByRequestId(d.getRequestId()), d.getProcessedAt(),
				d.getRejectedConfirmations(), d.getRejectionReasons(), d.isEverLookedInauthentic());
	}

	private static ConsoleRequestDetail.Signals toSignals(RequestSignals s) {
		var geo = s.getGeolocation();
		var ipDetails = geo == null || geo.getLookedUpAt() == null ? null
				: new ConsoleRequestDetail.IpDetails(geo.getCountry(), geo.getCountryCode(), geo.getRegion(),
						geo.getRegionName(), geo.getCity(), geo.getZip(), geo.getTimezone(), geo.getIsp(),
						geo.getOrg(), geo.getAsName(), geo.getFailure(), geo.getLookedUpAt());
		return new ConsoleRequestDetail.Signals(s.getIp() == null ? null : s.getIp().getHostAddress(),
				s.getApproximateLocation(), s.getDeviceFingerprint(), s.getDevice(), s.getTypingSpeedCpm(),
				s.getTypingPace(), s.getNightTime(), s.getTotalDurationSeconds(), s.getRequestsFromSameDevice(),
				geo == null ? null : geo.getStatus(), geo == null ? null : geo.getLatitude(),
				geo == null ? null : geo.getLongitude(), ipDetails, s.getCapturedAt(),
				s.getDeviceLocationStatus(), s.getDeviceLocation(), s.getDeviceCountryIso());
	}

	private static String escapeLike(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

}
