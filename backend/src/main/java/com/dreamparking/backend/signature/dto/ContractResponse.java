package com.dreamparking.backend.signature.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dreamparking.backend.signature.service.ContractTerms;

/** The account contract in force, for the app to show before signing (VDI-130). */
public record ContractResponse(@Schema(description = "Versión del contrato; la app la manda al firmar", example = "2026.1") String version,
		@Schema(description = "Texto completo del contrato, con saltos de línea \\n") String text,
		@Schema(description = "Huella SHA-256 del texto en UTF-8, 64 caracteres hexadecimales en minúsculas") String fingerprint) {

	public static ContractResponse of(ContractTerms terms) {
		return new ContractResponse(terms.version(), terms.text(), terms.fingerprint());
	}

}
