package com.dreamparking.backend.alert.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.alert.dto.AlertActionRequest;
import com.dreamparking.backend.alert.dto.AlertHistoryResponse;
import com.dreamparking.backend.alert.dto.AlertResponse;
import com.dreamparking.backend.alert.dto.AlertTypeResponse;
import com.dreamparking.backend.alert.dto.CloseAlertRequest;
import com.dreamparking.backend.alert.dto.InboxItemResponse;
import com.dreamparking.backend.alert.dto.RaiseAlertRequest;
import com.dreamparking.backend.alert.service.AlertService;

@RestController
@RequestMapping("/api/console")
public class AlertController {

	private final AlertService alertService;

	public AlertController(AlertService alertService) {
		this.alertService = alertService;
	}

	@GetMapping("/alerts")
	public List<InboxItemResponse> inbox() {
		return alertService.inbox();
	}

	@GetMapping("/alert-types")
	public List<AlertTypeResponse> types() {
		return alertService.types();
	}

	@GetMapping("/alerts/{alertId}")
	public AlertResponse get(@PathVariable UUID alertId) {
		return alertService.get(alertId);
	}

	@GetMapping("/alerts/{alertId}/history")
	public List<AlertHistoryResponse> history(@PathVariable UUID alertId) {
		return alertService.history(alertId);
	}

	@PostMapping("/alerts")
	@ResponseStatus(HttpStatus.CREATED)
	public AlertResponse raise(@Valid @RequestBody RaiseAlertRequest body) {
		return alertService.raise(body);
	}

	@PostMapping("/alerts/{alertId}/take")
	public AlertResponse take(@PathVariable UUID alertId, @Valid @RequestBody AlertActionRequest body) {
		return alertService.take(alertId, body.userId(), body.comment());
	}

	@PostMapping("/alerts/{alertId}/review")
	public AlertResponse startReview(@PathVariable UUID alertId, @Valid @RequestBody AlertActionRequest body) {
		return alertService.startReview(alertId, body.userId(), body.comment());
	}

	@PostMapping("/alerts/{alertId}/close")
	public AlertResponse close(@PathVariable UUID alertId, @Valid @RequestBody CloseAlertRequest body) {
		return alertService.close(alertId, body);
	}

}
