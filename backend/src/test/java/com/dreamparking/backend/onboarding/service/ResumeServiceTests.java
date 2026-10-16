package com.dreamparking.backend.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.dreamparking.backend.identity.entity.IdentityDocument;
import com.dreamparking.backend.identity.repository.IdentityDocumentRepository;
import com.dreamparking.backend.onboarding.dto.ResumeResponse;
import com.dreamparking.backend.onboarding.dto.ResumeResponse.StepState;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.onboarding.entity.enums.OnboardingStep;
import com.dreamparking.backend.onboarding.repository.ExpectedActivityRepository;
import com.dreamparking.backend.onboarding.repository.ExpectedActivityVersionRepository;
import com.dreamparking.backend.onboarding.repository.IncomeDeclarationRepository;
import com.dreamparking.backend.onboarding.repository.OnboardingRequestRepository;
import com.dreamparking.backend.onboarding.repository.RequestEventRepository;

/** The step a request resumes from when the DUI step is required (VDI-86); the optional case is in ResumeApiTests. */
class ResumeServiceTests {

	private final UUID id = UUID.randomUUID();

	private final OnboardingRequestRepository requests = mock(OnboardingRequestRepository.class);

	private final IdentityDocumentRepository identityDocuments = mock(IdentityDocumentRepository.class);

	private final RequestEventRepository events = mock(RequestEventRepository.class);

	@Test
	void whenTheDuiIsRequiredTheRequestResumesAtTheDui() {
		OnboardingRequest request = requestWithSteps(2);

		ResumeResponse response = service(true).resume(id);

		assertThat(request.getCompletedSteps()).isEqualTo((short) 2);
		assertThat(response.currentStep()).isEqualTo(OnboardingStep.IDENTITY_DOCUMENT);
		assertThat(response.steps().get(2).state()).isEqualTo(StepState.CURRENT);
		assertThat(response.steps().get(3).state()).isEqualTo(StepState.PENDING);
	}

	@Test
	void anOptionalDuiTheCustomerDidNotConfirmIsSkipped() {
		requestWithSteps(2);

		ResumeResponse response = service(false).resume(id);

		assertThat(response.steps().get(2).state()).isEqualTo(StepState.SKIPPED);
		assertThat(response.steps().get(2).savedAt()).isNull();
		assertThat(response.currentStep()).isEqualTo(OnboardingStep.INCOME);
		assertThat(response.saved().identityDocument()).isNull();
	}

	@Test
	void anOptionalDuiTheCustomerConfirmedCountsAsDoneNotSkipped() {
		requestWithSteps(3);
		IdentityDocument document = mock(IdentityDocument.class);
		when(document.isConfirmed()).thenReturn(true);
		when(identityDocuments.findById(id)).thenReturn(Optional.of(document));

		ResumeResponse response = service(false).resume(id);

		assertThat(response.steps().get(2).state()).isEqualTo(StepState.DONE);
		assertThat(response.currentStep()).isEqualTo(OnboardingStep.INCOME);
		assertThat(response.saved().identityDocument().confirmed()).isTrue();
	}

	@Test
	void aSingleNameAndBlankNamesGiveAGreetingOrNone() {
		OnboardingRequest request = requestWithSteps(2);
		ReflectionTestUtils.setField(request, "firstNames", "  Marta ");
		assertThat(service(true).resume(id).firstName()).isEqualTo("Marta");

		ReflectionTestUtils.setField(request, "firstNames", "   ");
		assertThat(service(true).resume(id).firstName()).isNull();
	}

	private OnboardingRequest requestWithSteps(int completedSteps) {
		OnboardingRequest request = new OnboardingRequest();
		ReflectionTestUtils.setField(request, "id", id);
		request.setCompletedSteps((short) completedSteps);
		ReflectionTestUtils.setField(request, "lastActivityAt", Instant.now());
		ReflectionTestUtils.setField(request, "dui", "01234567-8");
		when(requests.findById(id)).thenReturn(Optional.of(request));
		when(events.findByRequestIdOrderByOccurredAt(any())).thenReturn(List.of());
		when(identityDocuments.findById(id)).thenReturn(Optional.empty());
		return request;
	}

	private ResumeService service(boolean identityDocumentRequired) {
		return new ResumeService(requests, mock(IncomeDeclarationRepository.class),
				mock(ExpectedActivityRepository.class), mock(ExpectedActivityVersionRepository.class), identityDocuments,
				events, mock(OnboardingService.class), identityDocumentRequired, 7);
	}

}
