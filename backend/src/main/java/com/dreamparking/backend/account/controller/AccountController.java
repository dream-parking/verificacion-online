package com.dreamparking.backend.account.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.account.dto.AccountResponse;
import com.dreamparking.backend.account.dto.ChangeAccountStatusRequest;
import com.dreamparking.backend.account.dto.OpenAccountRequest;
import com.dreamparking.backend.account.service.AccountService;

@RestController
@RequestMapping("/api/console")
@Tag(name = "Consola · Cuentas", description = "Cuentas abiertas a partir de solicitudes enviadas")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

	private final AccountService accountService;

	public AccountController(AccountService accountService) {
		this.accountService = accountService;
	}

	@ApiResponse(responseCode = "201", description = "Cuenta registrada")
	@Operation(summary = "Registra la cuenta abierta en el core para una solicitud enviada",
			description = "Solo se guarda el token del core y los últimos 4 dígitos. Una cuenta por solicitud.")
	@ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content)
	@ApiResponse(responseCode = "404", description = "La solicitud no existe", content = @Content)
	@ApiResponse(responseCode = "409", description = "La solicitud no está enviada, ya tiene cuenta o el token ya existe", content = @Content)
	@PostMapping("/accounts")
	@ResponseStatus(HttpStatus.CREATED)
	public AccountResponse open(@Valid @RequestBody OpenAccountRequest body) {
		return accountService.open(body);
	}

	@ApiResponse(responseCode = "200", description = "Cuenta")
	@Operation(summary = "Una cuenta")
	@ApiResponse(responseCode = "404", description = "La cuenta no existe", content = @Content)
	@GetMapping("/accounts/{accountId}")
	public AccountResponse get(@PathVariable UUID accountId) {
		return accountService.get(accountId);
	}

	@ApiResponse(responseCode = "200", description = "Cuentas del cliente, de la más antigua a la más reciente")
	@Operation(summary = "Cuentas de un cliente")
	@GetMapping("/customers/{customerId}/accounts")
	public List<AccountResponse> byCustomer(@PathVariable UUID customerId) {
		return accountService.byCustomer(customerId);
	}

	@ApiResponse(responseCode = "200", description = "Cuenta con su nuevo estado")
	@Operation(summary = "Bloquea, reactiva o cierra una cuenta", description = "Una cuenta cerrada no se puede reactivar.")
	@ApiResponse(responseCode = "400", description = "Estado inválido", content = @Content)
	@ApiResponse(responseCode = "404", description = "La cuenta no existe", content = @Content)
	@ApiResponse(responseCode = "409", description = "La cuenta está cerrada", content = @Content)
	@PutMapping("/accounts/{accountId}/status")
	public AccountResponse changeStatus(@PathVariable UUID accountId,
			@Valid @RequestBody ChangeAccountStatusRequest body) {
		return accountService.changeStatus(accountId, body.status());
	}

}
