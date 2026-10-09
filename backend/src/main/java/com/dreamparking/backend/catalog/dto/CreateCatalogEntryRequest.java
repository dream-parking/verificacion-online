package com.dreamparking.backend.catalog.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dreamparking.backend.common.validation.Code;
import com.dreamparking.backend.common.validation.FreeText;

/** New catalog entry. {@code minUsd}/{@code maxUsd} only for range catalogs; a null bound means open-ended. */
public record CreateCatalogEntryRequest(
		@Schema(description = "Código único, en mayúsculas; no se puede cambiar después", example = "MAS_5000") @NotBlank @Size(max = 30) @Code String code,
		@Schema(description = "Texto que ve el cliente", example = "Más de USD 5,000") @NotBlank @Size(max = 80) @FreeText String label,
		@Schema(description = "Posición en la lista; sin valor va al final", example = "5") @Min(0) Short sortOrder,
		@Schema(description = "Si se muestra en la app; por defecto sí", example = "true") Boolean active,
		@Schema(description = "Primer monto del rango (solo catálogos de rangos)", example = "5000.01") @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal minUsd,
		@Schema(description = "Último monto del rango (solo catálogos de rangos)") @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal maxUsd) {
}
