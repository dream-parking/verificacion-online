package com.dreamparking.backend.onboarding.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.onboarding.dto.RequestEventResponse;
import com.dreamparking.backend.onboarding.dto.RequestListItemResponse;
import com.dreamparking.backend.onboarding.dto.RequestSignalsResponse;
import com.dreamparking.backend.onboarding.dto.RequestStepResponse;
import com.dreamparking.backend.onboarding.repository.OnboardingRequestRepository;
import com.dreamparking.backend.onboarding.repository.RequestEventRepository;
import com.dreamparking.backend.onboarding.repository.RequestListItemRepository;
import com.dreamparking.backend.onboarding.repository.RequestSignalsRepository;
import com.dreamparking.backend.onboarding.repository.RequestStepRepository;

/** Read side of onboarding requests for the KYC/AML console. */
@Service
@Transactional(readOnly = true)
public class ConsoleRequestService {

	private final OnboardingRequestRepository requests;

	private final RequestListItemRepository listItems;

	private final RequestSignalsRepository signals;

	private final RequestEventRepository events;

	private final RequestStepRepository steps;

	public ConsoleRequestService(OnboardingRequestRepository requests, RequestListItemRepository listItems,
			RequestSignalsRepository signals, RequestEventRepository events, RequestStepRepository steps) {
		this.requests = requests;
		this.listItems = listItems;
		this.signals = signals;
		this.events = events;
		this.steps = steps;
	}

	/** All requests, most recent first. */
	public List<RequestListItemResponse> list() {
		return listItems.findAllByOrderByDateDesc().stream().map(RequestListItemResponse::of).toList();
	}

	public RequestSignalsResponse signals(UUID requestId) {
		return signals.findById(requestId)
			.map(RequestSignalsResponse::of)
			.orElseThrow(() -> notFound(requestId));
	}

	public List<RequestEventResponse> timeline(UUID requestId) {
		requireExists(requestId);
		return events.findByRequestIdOrderByOccurredAt(requestId).stream().map(RequestEventResponse::of).toList();
	}

	public List<RequestStepResponse> steps(UUID requestId) {
		requireExists(requestId);
		return steps.findByRequestIdOrderByStartedAt(requestId).stream().map(RequestStepResponse::of).toList();
	}

	private void requireExists(UUID requestId) {
		if (!requests.existsById(requestId)) {
			throw notFound(requestId);
		}
	}

	private static NotFoundException notFound(UUID requestId) {
		return new NotFoundException("Onboarding request not found: " + requestId);
	}

}
