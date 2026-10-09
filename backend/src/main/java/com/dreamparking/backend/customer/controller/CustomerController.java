package com.dreamparking.backend.customer.controller;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.common.validation.Dui;
import com.dreamparking.backend.customer.dto.CustomerResponse;
import com.dreamparking.backend.customer.service.CustomerService;

@RestController
@RequestMapping("/api/console/customers")
@Tag(name = "Consola · Clientes", description = "Clientes identificados por su DUI")
@SecurityRequirement(name = "bearerAuth")
public class CustomerController {

	private final CustomerService customerService;

	public CustomerController(CustomerService customerService) {
		this.customerService = customerService;
	}

	@ApiResponse(responseCode = "200", description = "Cliente")
	@Operation(summary = "Un cliente")
	@ApiResponse(responseCode = "404", description = "El cliente no existe", content = @Content)
	@GetMapping("/{customerId}")
	public CustomerResponse get(@PathVariable UUID customerId) {
		return customerService.get(customerId);
	}

	@ApiResponse(responseCode = "200", description = "Cliente")
	@Operation(summary = "Busca un cliente por DUI", description = "Formato 00000000-0.")
	@ApiResponse(responseCode = "404", description = "No hay cliente con ese DUI", content = @Content)
	@GetMapping(params = "dui")
	public CustomerResponse getByDui(@RequestParam @Dui String dui) {
		return customerService.getByDui(dui);
	}

}
