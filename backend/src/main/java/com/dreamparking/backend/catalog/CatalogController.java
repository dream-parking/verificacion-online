package com.dreamparking.backend.catalog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalogs")
@Tag(name = "Catálogos", description = "Listas de referencia que usa el flujo de onboarding")
public class CatalogController {

	private final CatalogService catalogService;

	public CatalogController(CatalogService catalogService) {
		this.catalogService = catalogService;
	}

	@GetMapping
	@Operation(summary = "Catálogos activos",
			description = "Fuentes de ingreso, rangos de ingreso y tipos de transacción, en orden de despliegue.")
	public CatalogsResponse catalogs() {
		return catalogService.activeCatalogs();
	}

}
