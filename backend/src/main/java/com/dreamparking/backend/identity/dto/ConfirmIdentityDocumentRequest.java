package com.dreamparking.backend.identity.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dreamparking.backend.common.validation.Dui;
import com.dreamparking.backend.common.validation.PersonName;

/** The DUI data as the customer confirmed or corrected it on the confirmation screen. Same formats as the basic data. */
public record ConfirmIdentityDocumentRequest(
		@Schema(description = "Número de DUI", example = "04567891-2") @NotBlank @Dui String dui,
		@Schema(description = "Nombres", example = "Marta Alejandra") @NotBlank @Size(max = 100) @PersonName String firstNames,
		@Schema(description = "Apellidos", example = "Rivas Cruz") @NotBlank @Size(max = 100) @PersonName String lastNames,
		@Schema(description = "Fecha de nacimiento", example = "1991-03-14") @NotNull @Past LocalDate birthDate,
		@Schema(description = "Fecha de vencimiento del DUI", example = "2031-07-22") @NotNull LocalDate expiryDate) {
}
