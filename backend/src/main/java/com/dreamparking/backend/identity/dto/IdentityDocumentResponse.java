package com.dreamparking.backend.identity.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dreamparking.backend.identity.entity.IdentityDocument;
import com.dreamparking.backend.identity.entity.enums.OcrStatus;

/**
 * What the app receives after the DUI capture, to pre-fill the basic data for the customer to confirm or correct.
 * The fraud signals (authenticity, confidence) are only shown in the console.
 */
public record IdentityDocumentResponse(
		@Schema(description = "`READ`: datos leídos. `FAILED`: el lector no está disponible y la persona escribe sus datos a mano") OcrStatus status,
		@Schema(description = "Capturas hechas en esta solicitud", example = "1") short attempts,
		@Schema(description = "Número de DUI leído", example = "01234567-8") String dui,
		@Schema(description = "Nombres leídos", example = "María José") String firstNames,
		@Schema(description = "Apellidos leídos", example = "Pérez López") String lastNames,
		@Schema(description = "Fecha de nacimiento", example = "1990-04-12") LocalDate birthDate,
		@Schema(description = "Fecha de expedición", example = "2021-06-01") LocalDate issueDate,
		@Schema(description = "Fecha de vencimiento", example = "2029-06-01") LocalDate expiryDate,
		@Schema(description = "Sexo: M o F", example = "F") String gender) {

	public static IdentityDocumentResponse of(IdentityDocument document) {
		return new IdentityDocumentResponse(document.getStatus(), document.getAttempts(), document.getDui(),
				document.getFirstNames(), document.getLastNames(), document.getBirthDate(), document.getIssueDate(),
				document.getExpiryDate(), document.getGender());
	}

}
