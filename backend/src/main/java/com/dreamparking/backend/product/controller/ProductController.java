package com.dreamparking.backend.product.controller;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.product.dto.ProductResponse;
import com.dreamparking.backend.product.service.ProductCatalogService;

/** The products an approved request can choose from, for the app (VDI-146). */
@RestController
@Tag(name = "Onboarding", description = "Pasos de la solicitud de apertura de cuenta (app móvil)")
public class ProductController {

	private final ProductCatalogService service;

	public ProductController(ProductCatalogService service) {
		this.service = service;
	}

	@ApiResponse(responseCode = "200", description = "Los productos a los que califica la solicitud, con sus condiciones")
	@Operation(summary = "Productos a los que califica la solicitud",
			description = "Devuelve solo los productos a los que califica la solicitud, con sus condiciones principales; "
					+ "los que no califica no aparecen. Solo una solicitud enviada y aprobada (`COMPLETED`) puede "
					+ "consultarlos. Regla de elegibilidad provisional: una solicitud aprobada califica a todos los "
					+ "productos activos.")
	@ApiResponse(responseCode = "400", description = "Falta `requestId` o no es un UUID", content = @Content)
	@ApiResponse(responseCode = "404", description = "La solicitud no existe", content = @Content)
	@ApiResponse(responseCode = "409", description = "La solicitud no está aprobada (en progreso, en revisión reforzada, "
			+ "en espera del RNPN o abandonada)", content = @Content)
	@GetMapping("/api/onboarding/products")
	public List<ProductResponse> products(
			@Parameter(description = "Id de la solicitud, el mismo que usa la app en el resto del flujo") @RequestParam UUID requestId) {
		return service.availableFor(requestId);
	}

}
