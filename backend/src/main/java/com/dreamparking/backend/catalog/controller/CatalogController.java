package com.dreamparking.backend.catalog.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.catalog.dto.CatalogsResponse;
import com.dreamparking.backend.catalog.service.CatalogService;

@RestController
@RequestMapping("/api/catalogs")
public class CatalogController {

	private final CatalogService catalogService;

	public CatalogController(CatalogService catalogService) {
		this.catalogService = catalogService;
	}

	@GetMapping
	public CatalogsResponse catalogs() {
		return catalogService.activeCatalogs();
	}

}
