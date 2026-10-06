package com.dreamparking.backend.console.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.console.dto.AccessAuditResponse;
import com.dreamparking.backend.console.dto.ConsoleUserResponse;
import com.dreamparking.backend.console.dto.CreateConsoleUserRequest;
import com.dreamparking.backend.console.service.AccessAuditService;
import com.dreamparking.backend.console.service.ConsoleUserService;

@RestController
@RequestMapping("/api/console/users")
public class ConsoleUserController {

	private final ConsoleUserService consoleUserService;

	private final AccessAuditService accessAuditService;

	public ConsoleUserController(ConsoleUserService consoleUserService, AccessAuditService accessAuditService) {
		this.consoleUserService = consoleUserService;
		this.accessAuditService = accessAuditService;
	}

	@GetMapping
	public List<ConsoleUserResponse> list(@RequestParam(defaultValue = "true") boolean activeOnly) {
		return consoleUserService.list(activeOnly);
	}

	@GetMapping("/{userId}")
	public ConsoleUserResponse get(@PathVariable UUID userId) {
		return consoleUserService.get(userId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ConsoleUserResponse create(@Valid @RequestBody CreateConsoleUserRequest body) {
		return consoleUserService.create(body);
	}

	@PostMapping("/{userId}/deactivate")
	public ConsoleUserResponse deactivate(@PathVariable UUID userId) {
		return consoleUserService.deactivate(userId);
	}

	@GetMapping("/{userId}/access-audit")
	public List<AccessAuditResponse> accessAudit(@PathVariable UUID userId) {
		return accessAuditService.byUser(userId);
	}

}
