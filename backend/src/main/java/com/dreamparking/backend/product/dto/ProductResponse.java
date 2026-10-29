package com.dreamparking.backend.product.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dreamparking.backend.product.entity.Product;

/** A product the request qualifies for, with its main conditions (VDI-146). */
public record ProductResponse(@Schema(description = "Código estable del producto", example = "AHORRO_BASICA") String code,
		@Schema(example = "Cuenta de ahorro básica") String name,
		@Schema(description = "Qué ofrece, para comparar productos") String description,
		@Schema(description = "Cuota mensual en USD", example = "0.00") BigDecimal monthlyFeeUsd,
		@Schema(description = "Depósito mínimo para abrir, en USD", example = "10.00") BigDecimal minimumOpeningDepositUsd,
		@Schema(description = "Interés anual en porcentaje", example = "0.50") BigDecimal annualInterestRate) {

	public static ProductResponse of(Product product) {
		return new ProductResponse(product.getCode(), product.getName(), product.getDescription(),
				product.getMonthlyFeeUsd(), product.getMinimumOpeningDepositUsd(), product.getAnnualInterestRate());
	}

}
