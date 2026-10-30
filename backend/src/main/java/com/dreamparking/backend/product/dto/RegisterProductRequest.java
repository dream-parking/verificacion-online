package com.dreamparking.backend.product.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** The product the applicant chooses; nothing else is asked again (VDI-148). */
public record RegisterProductRequest(
		@Schema(description = "Id de la solicitud aprobada, el mismo que usa la app en el resto del flujo") @NotNull UUID requestId,
		@Schema(description = "Código del producto elegido, de GET /api/onboarding/products", example = "AHORRO_BASICA") @NotBlank @Size(max = 30) @Pattern(regexp = "[A-Z0-9_]+", message = "Debe ser un código de producto: mayúsculas, números y _") String productCode) {
}
