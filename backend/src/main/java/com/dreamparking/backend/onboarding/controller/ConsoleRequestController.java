package com.dreamparking.backend.onboarding.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.onboarding.dto.RequestEventResponse;
import com.dreamparking.backend.onboarding.dto.RequestListItemResponse;
import com.dreamparking.backend.onboarding.dto.RequestSignalsResponse;
import com.dreamparking.backend.onboarding.dto.RequestStepResponse;
import com.dreamparking.backend.onboarding.service.ConsoleRequestService;

@RestController
@RequestMapping("/api/console/requests")
public class ConsoleRequestController {

	private final ConsoleRequestService consoleRequestService;

	public ConsoleRequestController(ConsoleRequestService consoleRequestService) {
		this.consoleRequestService = consoleRequestService;
	}

	@GetMapping
	public List<RequestListItemResponse> list() {
		return consoleRequestService.list();
	}

	@GetMapping("/{requestId}/signals")
	public RequestSignalsResponse signals(@PathVariable UUID requestId) {
		return consoleRequestService.signals(requestId);
	}

	@GetMapping("/{requestId}/timeline")
	public List<RequestEventResponse> timeline(@PathVariable UUID requestId) {
		return consoleRequestService.timeline(requestId);
	}

	@GetMapping("/{requestId}/steps")
	public List<RequestStepResponse> steps(@PathVariable UUID requestId) {
		return consoleRequestService.steps(requestId);
	}

}
