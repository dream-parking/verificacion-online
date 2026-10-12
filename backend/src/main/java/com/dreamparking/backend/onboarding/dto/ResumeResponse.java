package com.dreamparking.backend.onboarding.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dreamparking.backend.identity.entity.enums.OcrStatus;
import com.dreamparking.backend.onboarding.entity.enums.OnboardingStep;

/**
 * Where a request stopped and what the customer already saved, so the app can offer to continue from the last step
 * (VDI-86). {@code saved} and {@code currentStep} are null unless the request can be resumed ({@code state}
 * {@code RESUMABLE}); {@code number} is only set once the request is submitted.
 */
public record ResumeResponse(UUID requestId, ResumeState state, String number, String firstName,
		OnboardingStep currentStep, short completedSteps, Instant lastActivityAt, Instant expiresAt,
		List<StepProgress> steps, Saved saved) {

	/** Whether the request can be continued, and why not when it cannot. */
	public enum ResumeState {

		/** In progress and active inside the resume window: continue from {@code currentStep}. */
		RESUMABLE,

		/** In progress but untouched for longer than the resume window: start a new request. */
		EXPIRED,

		/** Already submitted. */
		COMPLETED,

		/** Abandoned, for instance because the customer chose to start over. */
		ABANDONED

	}

	public enum StepState {

		/** Saved. */
		DONE,

		/** Not asked for this request (the DUI step while it is optional). */
		SKIPPED,

		/** The step the customer continues from. */
		CURRENT,

		PENDING

	}

	/** One step of the flow, in order. {@code savedAt} is when it was saved (null if it has not been). */
	@Schema(name = "ResumeStepProgress")
	public record StepProgress(OnboardingStep step, StepState state, Instant savedAt) {
	}

	/** What the customer saved so far, to fill the screens again. Each part is null until its step is saved. */
	@Schema(name = "ResumeSaved")
	public record Saved(BasicData basicData, IdentityDocument identityDocument, Income income,
			ExpectedActivity expectedActivity) {
	}

	@Schema(name = "ResumeBasicData")
	public record BasicData(String dui, String firstNames, String lastNames, String mobilePhone) {
	}

	/** {@code confirmed}: the customer confirmed the data read from the photos. */
	@Schema(name = "ResumeIdentityDocument")
	public record IdentityDocument(OcrStatus ocrStatus, boolean confirmed) {
	}

	@Schema(name = "ResumeIncome")
	public record Income(String sourceCode, String sourceDetail, String rangeCode) {
	}

	@Schema(name = "ResumeExpectedActivity")
	public record ExpectedActivity(String transactionTypeCode, String monthlyAmountRangeCode, short version) {
	}

}
