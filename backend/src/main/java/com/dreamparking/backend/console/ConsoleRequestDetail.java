package com.dreamparking.backend.console;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.dreamparking.backend.onboarding.OnboardingStep;
import com.dreamparking.backend.onboarding.RequestEventType;
import com.dreamparking.backend.onboarding.RequestStatus;
import com.dreamparking.backend.onboarding.TypingPace;
import com.dreamparking.backend.risk.RiskAssessmentResponse;
import com.dreamparking.backend.risk.RiskLevel;

/**
 * Everything the console's request detail screen shows. {@code income}, {@code expectedActivity}, {@code risk} and
 * {@code signals} are null until the customer reaches that point of the flow.
 */
public record ConsoleRequestDetail(UUID id, String number, RequestStatus status, short completedSteps,
		RiskLevel riskLevel, Instant startedAt, Instant submittedAt, Applicant applicant, Income income,
		ExpectedActivity expectedActivity, RiskAssessmentResponse risk, Signals signals, List<StepTime> steps,
		List<TimelineEntry> timeline) {

	/** Personal data as declared; internal use only. */
	public record Applicant(String firstNames, String lastNames, String dui, String mobilePhone) {
	}

	/** Declared income (VDI-23). {@code registeredAt} is the original declaration time and never changes. */
	public record Income(String sourceCode, String sourceLabel, String sourceDetail, String rangeCode,
			String rangeLabel, Instant registeredAt, Instant updatedAt) {
	}

	/** Declared expected activity (VDI-24). */
	public record ExpectedActivity(String transactionTypeCode, String transactionTypeLabel,
			String monthlyAmountRangeCode, String monthlyAmountRangeLabel, Instant registeredAt, Instant updatedAt) {
	}

	/** Device and behavior signals (VDI-12). */
	public record Signals(String ip, String approximateLocation, String deviceFingerprint, String device,
			Short typingSpeedCpm, TypingPace typingPace, Boolean nightTime, Long totalDurationSeconds,
			Long requestsFromSameDevice) {
	}

	public record StepTime(OnboardingStep step, Instant startedAt, Instant completedAt, Integer durationSeconds,
			Short attempts) {
	}

	public record TimelineEntry(RequestEventType type, String description, String actor, Instant occurredAt) {
	}

}
