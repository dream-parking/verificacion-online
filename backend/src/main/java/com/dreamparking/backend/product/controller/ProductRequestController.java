package com.dreamparking.backend.product.controller;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.product.dto.ProductRequestResponse;
import com.dreamparking.backend.product.dto.RegisterProductRequest;
import com.dreamparking.backend.product.service.ProductRequestService;

/** The product an approved request chooses, for the app (VDI-148). */
@RestController
@Tag(name = "Onboarding", description = "Pasos de la solicitud de apertura de cuenta (app móvil)")
public class ProductRequestController {

	private final ProductRequestService service;

	public ProductRequestController(ProductRequestService service) {
		this.service = service;
	}

	@ApiResponse(responseCode = "201", description = "Producto registrado; la respuesta lo confirma con sus condiciones")
	@Operation(summary = "Registra el producto elegido por una solicitud aprobada",
			description = "Solo se envía el código del producto: todo lo demás (la persona, el DUI, el perfil declarado) se "
					+ "toma de la solicitud ya registrada, sin pedir ningún dato otra vez. Una solicitud elige una sola vez, y "
					+ "solo un producto al que califica (los de `GET /api/onboarding/products`). La elección queda en la "
					+ "línea de tiempo de la solicitud.")
	@ApiResponse(responseCode = "400", description = "Falta `requestId` o `productCode`, o no tienen el formato",
			content = @Content)
	@ApiResponse(responseCode = "404", description = "La solicitud o el producto no existe", content = @Content)
	@ApiResponse(responseCode = "409", description = "La solicitud no está aprobada, no califica para el producto o ya "
			+ "eligió uno", content = @Content)
	@PostMapping("/api/onboarding/product-request")
	@ResponseStatus(HttpStatus.CREATED)
	public ProductRequestResponse register(@Valid @RequestBody RegisterProductRequest body) {
		return service.register(body);
	}

}
