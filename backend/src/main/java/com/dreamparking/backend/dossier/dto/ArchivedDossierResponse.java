package com.dreamparking.backend.dossier.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * An archived binding dossier found by number (VDI-135): the request it belongs to, by its number, and the dossier as
 * it was frozen when the customer signed.
 */
public record ArchivedDossierResponse(
		@Schema(description = "Número de la solicitud del expediente", example = "SOL-2026-00001") String requestNumber,
		@Schema(description = "Número del cliente; vacío si la solicitud aún no tenía cliente numerado", example = "CLI-0000001") String customerNumber,
		BindingDossierResponse dossier) {
}
