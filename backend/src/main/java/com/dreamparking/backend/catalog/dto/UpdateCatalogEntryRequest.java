package com.dreamparking.backend.catalog.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Edits a catalog entry. Bounds of a range already used by a request cannot change (they are part of its KYC
 * file): create a new range and deactivate this one instead.
 */
public record UpdateCatalogEntryRequest(
		@Schema(description = "Texto que ve el cliente", example = "Hasta USD 500") @NotBlank @Size(max = 80) String label,
		@Schema(description = "Posición en la lista", example = "1") @NotNull @Min(0) Short sortOrder,
		@Schema(description = "Si se muestra en la app", example = "true") @NotNull Boolean active,
		@Schema(description = "Primer monto del rango (solo catálogos de rangos)") @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal minUsd,
		@Schema(description = "Último monto del rango (solo catálogos de rangos)", example = "500") @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal maxUsd) {
}
