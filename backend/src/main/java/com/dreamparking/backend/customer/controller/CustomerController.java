package com.dreamparking.backend.customer.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.customer.dto.CustomerResponse;
import com.dreamparking.backend.customer.service.CustomerService;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

	private final CustomerService customerService;

	public CustomerController(CustomerService customerService) {
		this.customerService = customerService;
	}

	@GetMapping("/{customerId}")
	public CustomerResponse get(@PathVariable UUID customerId) {
		return customerService.get(customerId);
	}

	@GetMapping(params = "dui")
	public CustomerResponse getByDui(@RequestParam String dui) {
		return customerService.getByDui(dui);
	}

}
