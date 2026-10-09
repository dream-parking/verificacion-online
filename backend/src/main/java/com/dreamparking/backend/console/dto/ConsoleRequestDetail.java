package com.dreamparking.backend.console.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dreamparking.backend.identity.entity.enums.DocumentSide;
import com.dreamparking.backend.identity.entity.enums.OcrStatus;
import com.dreamparking.backend.identity.entity.enums.UnreadableReason;
import com.dreamparking.backend.onboarding.entity.enums.LocationStatus;
import com.dreamparking.backend.onboarding.entity.enums.OnboardingStep;
import com.dreamparking.backend.onboarding.entity.enums.RequestEventType;
import com.dreamparking.backend.onboarding.entity.enums.RequestStatus;
import com.dreamparking.backend.onboarding.entity.enums.TypingPace;
import com.dreamparking.backend.risk.dto.RiskAssessmentResponse;
import com.dreamparking.backend.risk.entity.enums.RiskLevel;

/**
 * Everything the console's request detail screen shows. {@code identityDocument}, {@code income},
 * {@code expectedActivity}, {@code risk} and {@code signals} are null until the customer reaches that point of the flow.
 */
public record ConsoleRequestDetail(UUID id, String number, RequestStatus status, short completedSteps,
		RiskLevel riskLevel, Instant startedAt, Instant submittedAt, Applicant applicant,
		IdentityDocument identityDocument, Income income, ExpectedActivity expectedActivity,
		RiskAssessmentResponse risk, Signals signals, List<StepTime> steps, List<TimelineEntry> timeline) {

	/** Personal data as declared; internal use only. */
	@Schema(name = "ConsoleRequestApplicant")
	public record Applicant(String firstNames, String lastNames, String dui, String mobilePhone) {
	}

	/**
	 * What was read from the DUI (VDI-79) and how it compares with what the customer confirmed. The photos are
	 * downloaded one by one from {@code /identity-document/{side}}; {@code photos} lists the sides on file.
	 * {@code checkDigitValid} is about the number read; {@code expired} compares the expiry date with today.
	 */
	@Schema(name = "ConsoleRequestIdentityDocument")
	public record IdentityDocument(OcrStatus ocrStatus, String ocrModel, short attempts,
			UnreadableReason unreadableReason, String failure, String dui, String firstNames, String lastNames,
			LocalDate birthDate, LocalDate issueDate, LocalDate expiryDate, String gender, Boolean checkDigitValid,
			Boolean expired, Boolean looksAuthentic, BigDecimal confidence, Boolean duiMatchesDeclared,
			List<String> correctedFields, List<DocumentSide> photos, Instant processedAt) {
	}

	/** Declared income (VDI-23). {@code registeredAt} is the original declaration time and never changes. */
	@Schema(name = "ConsoleRequestIncome")
	public record Income(String sourceCode, String sourceLabel, String sourceDetail, String rangeCode,
			String rangeLabel, Instant registeredAt, Instant updatedAt) {
	}

	/** Declared expected activity (VDI-24). */
	@Schema(name = "ConsoleRequestExpectedActivity")
	public record ExpectedActivity(String transactionTypeCode, String transactionTypeLabel,
			String monthlyAmountRangeCode, String monthlyAmountRangeLabel, Instant registeredAt, Instant updatedAt) {
	}

	/** Device and behavior signals (VDI-12). */
	@Schema(name = "ConsoleRequestSignals")
	public record Signals(String ip, String approximateLocation, String deviceFingerprint, String device,
			Short typingSpeedCpm, TypingPace typingPace, Boolean nightTime, Long totalDurationSeconds,
			Long requestsFromSameDevice, LocationStatus locationStatus, BigDecimal latitude, BigDecimal longitude,
			IpDetails ipDetails) {
	}

	/**
	 * What the IP geolocation provider returned (VDI-67). When the location is {@code UNAVAILABLE} only
	 * {@code failure} and {@code lookedUpAt} are filled in.
	 */
	@Schema(name = "ConsoleRequestIpDetails")
	public record IpDetails(String country, String countryCode, String region, String regionName, String city,
			String zip, String timezone, String isp, String org, String asName, String failure, Instant lookedUpAt) {
	}

	@Schema(name = "ConsoleRequestStepTime")
	public record StepTime(OnboardingStep step, Instant startedAt, Instant completedAt, Integer durationSeconds,
			Short attempts) {
	}

	@Schema(name = "ConsoleRequestTimelineEntry")
	public record TimelineEntry(RequestEventType type, String description, String actor, Instant occurredAt) {
	}

}
