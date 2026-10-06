package com.dreamparking.backend.account.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.account.dto.AccountResponse;
import com.dreamparking.backend.account.dto.ChangeAccountStatusRequest;
import com.dreamparking.backend.account.dto.OpenAccountRequest;
import com.dreamparking.backend.account.service.AccountService;

@RestController
public class AccountController {

	private final AccountService accountService;

	public AccountController(AccountService accountService) {
		this.accountService = accountService;
	}

	@PostMapping("/api/accounts")
	@ResponseStatus(HttpStatus.CREATED)
	public AccountResponse open(@Valid @RequestBody OpenAccountRequest body) {
		return accountService.open(body);
	}

	@GetMapping("/api/accounts/{accountId}")
	public AccountResponse get(@PathVariable UUID accountId) {
		return accountService.get(accountId);
	}

	@GetMapping("/api/customers/{customerId}/accounts")
	public List<AccountResponse> byCustomer(@PathVariable UUID customerId) {
		return accountService.byCustomer(customerId);
	}

	@PutMapping("/api/accounts/{accountId}/status")
	public AccountResponse changeStatus(@PathVariable UUID accountId,
			@Valid @RequestBody ChangeAccountStatusRequest body) {
		return accountService.changeStatus(accountId, body.status());
	}

}
