package com.dreamparking.backend.product.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dreamparking.backend.product.entity.ProductRequest;

/** The product request registered, to confirm it to the applicant (VDI-148). */
public record ProductRequestResponse(Long id, UUID requestId,
		@Schema(description = "Número de la solicitud", example = "SOL-2026-00001") String requestNumber,
		@Schema(description = "El producto elegido, con sus condiciones") ProductResponse product,
		@Schema(description = "Cuándo se registró la elección") Instant requestedAt) {

	public static ProductRequestResponse of(ProductRequest productRequest) {
		return new ProductRequestResponse(productRequest.getId(), productRequest.getRequest().getId(),
				productRequest.getRequest().getNumber(), ProductResponse.of(productRequest.getProduct()),
				productRequest.getRequestedAt());
	}

}
