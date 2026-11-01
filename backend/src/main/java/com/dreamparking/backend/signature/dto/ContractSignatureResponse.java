package com.dreamparking.backend.signature.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dreamparking.backend.signature.entity.ContractSignature;

/**
 * The signature of the contract of a request (VDI-130). The device and IP are kept as evidence but not returned to the
 * app.
 */
public record ContractSignatureResponse(UUID requestId, String contractVersion,
		@Schema(description = "Huella SHA-256 del texto firmado") String contractFingerprint, Instant signedAt,
		@Schema(description = "Servicio de firma: `simulated` mientras no hay un proveedor real") String provider,
		@Schema(description = "true mientras la firma es del servicio simulado") boolean simulated) {

	public static ContractSignatureResponse of(ContractSignature signature) {
		return new ContractSignatureResponse(signature.getRequest().getId(), signature.getContractVersion(),
				signature.getContractFingerprint(), signature.getSignedAt(), signature.getProvider(),
				signature.isSimulated());
	}

}
