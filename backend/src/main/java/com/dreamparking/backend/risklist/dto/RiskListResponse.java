package com.dreamparking.backend.risklist.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dreamparking.backend.risklist.entity.RiskList;
import com.dreamparking.backend.risklist.entity.enums.RiskListType;

/** An internal risk list and the weight its matches carry for the score. */
public record RiskListResponse(String code, String name, RiskListType type,
		@Schema(description = "Peso de sus coincidencias para el score, de 1 a 100") int weight) {

	public static RiskListResponse of(RiskList list) {
		return new RiskListResponse(list.getCode(), list.getName(), list.getType(), list.getWeight());
	}

}
