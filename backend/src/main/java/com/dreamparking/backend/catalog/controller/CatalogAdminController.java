package com.dreamparking.backend.catalog.controller;

import java.util.List;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.catalog.dto.CatalogEntryResponse;
import com.dreamparking.backend.catalog.dto.CatalogKind;
import com.dreamparking.backend.catalog.dto.CreateCatalogEntryRequest;
import com.dreamparking.backend.catalog.dto.PrivacyNoticeResponse;
import com.dreamparking.backend.catalog.dto.PublishPrivacyNoticeRequest;
import com.dreamparking.backend.catalog.dto.UpdateCatalogEntryRequest;
import com.dreamparking.backend.catalog.service.CatalogAdminService;
import com.dreamparking.backend.catalog.service.PrivacyNoticeService;
import com.dreamparking.backend.security.AuthenticatedUser;

/** Maintenance of the onboarding catalogs and the privacy notice. Administrators only. */
@RestController
@RequestMapping("/api/console/admin")
@Tag(name = "Consola · Administración de catálogos", description = "Catálogos de la app y aviso de privacidad (administradores)")
@SecurityRequirement(name = "bearerAuth")
public class CatalogAdminController {

	private static final String CATALOGS = "income-sources, income-ranges, monthly-amount-ranges o transaction-types";

	private final CatalogAdminService catalogAdminService;

	private final PrivacyNoticeService privacyNoticeService;

	public CatalogAdminController(CatalogAdminService catalogAdminService, PrivacyNoticeService privacyNoticeService) {
		this.catalogAdminService = catalogAdminService;
		this.privacyNoticeService = privacyNoticeService;
	}

	@ApiResponse(responseCode = "200", description = "Entradas del catálogo, activas e inactivas, en orden")
	@Operation(summary = "Entradas de un catálogo", description = "Catálogos: " + CATALOGS + ".")
	@ApiResponse(responseCode = "403", description = "Solo administradores", content = @Content)
	@ApiResponse(responseCode = "404", description = "El catálogo no existe", content = @Content)
	@GetMapping("/catalogs/{catalog}")
	public List<CatalogEntryResponse> list(@PathVariable @Parameter(schema = @Schema(allowableValues = {
			"income-sources", "income-ranges", "monthly-amount-ranges", "transaction-types" })) String catalog) {
		return catalogAdminService.list(CatalogKind.fromSlug(catalog));
	}

	@ApiResponse(responseCode = "201", description = "Entrada creada")
	@Operation(summary = "Agrega una entrada a un catálogo",
			description = "Los rangos necesitan al menos un límite y no pueden solaparse con otro rango activo del mismo catálogo.")
	@ApiResponse(responseCode = "400", description = "Datos inválidos o límites incoherentes", content = @Content)
	@ApiResponse(responseCode = "403", description = "Solo administradores", content = @Content)
	@ApiResponse(responseCode = "404", description = "El catálogo no existe", content = @Content)
	@ApiResponse(responseCode = "409", description = "El código ya existe o el rango se solapa con otro", content = @Content)
	@PostMapping("/catalogs/{catalog}")
	@ResponseStatus(HttpStatus.CREATED)
	public CatalogEntryResponse create(@PathVariable String catalog, @Valid @RequestBody CreateCatalogEntryRequest body,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser admin) {
		return catalogAdminService.create(CatalogKind.fromSlug(catalog), body, admin.id());
	}

	@ApiResponse(responseCode = "200", description = "Entrada actualizada")
	@Operation(summary = "Edita o desactiva una entrada de un catálogo",
			description = "Desactivarla la quita de la app sin tocar las solicitudes que ya la usan. Los límites de un rango "
					+ "que ya está en algún expediente no se pueden cambiar: cree un rango nuevo y desactive este.")
	@ApiResponse(responseCode = "400", description = "Datos inválidos o límites incoherentes", content = @Content)
	@ApiResponse(responseCode = "403", description = "Solo administradores", content = @Content)
	@ApiResponse(responseCode = "404", description = "El catálogo o la entrada no existen", content = @Content)
	@ApiResponse(responseCode = "409", description = "Rango en uso, solapado, o sería la última entrada activa", content = @Content)
	@PutMapping("/catalogs/{catalog}/{code}")
	public CatalogEntryResponse update(@PathVariable String catalog, @PathVariable String code,
			@Valid @RequestBody UpdateCatalogEntryRequest body,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser admin) {
		return catalogAdminService.update(CatalogKind.fromSlug(catalog), code, body, admin.id());
	}

	@ApiResponse(responseCode = "200", description = "Versiones, de la más reciente a la más antigua")
	@Operation(summary = "Versiones del aviso de privacidad", description = "La vigente es la que no tiene `validTo`.")
	@ApiResponse(responseCode = "403", description = "Solo administradores", content = @Content)
	@GetMapping("/privacy-notices")
	public List<PrivacyNoticeResponse> privacyNotices() {
		return privacyNoticeService.list();
	}

	@ApiResponse(responseCode = "201", description = "Versión publicada y vigente desde ahora")
	@Operation(summary = "Publica una versión nueva del aviso de privacidad",
			description = "La versión anterior deja de estar vigente en el mismo instante. Los consentimientos ya dados "
					+ "siguen apuntando a la versión que se aceptó.")
	@ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content)
	@ApiResponse(responseCode = "403", description = "Solo administradores", content = @Content)
	@ApiResponse(responseCode = "409", description = "La versión ya existe", content = @Content)
	@PostMapping("/privacy-notices")
	@ResponseStatus(HttpStatus.CREATED)
	public PrivacyNoticeResponse publishPrivacyNotice(@Valid @RequestBody PublishPrivacyNoticeRequest body,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser admin) {
		return privacyNoticeService.publish(body, admin.id());
	}

}
