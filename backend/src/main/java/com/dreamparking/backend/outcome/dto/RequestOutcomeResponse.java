package com.dreamparking.backend.outcome.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dreamparking.backend.outcome.entity.RequestOutcome;
import com.dreamparking.backend.outcome.entity.enums.CaseOutcome;
import com.dreamparking.backend.risklist.entity.enums.RiskListDataType;

/** An outcome registered on a request, with the data it put on the risk lists and the data it took off. */
public record RequestOutcomeResponse(Long id, UUID requestId, CaseOutcome outcome, String reason, String registeredBy,
		Instant registeredAt,
		@Schema(description = "Datos que este desenlace puso en la lista de fraude confirmado. Vacío si no puso ninguno (falso positivo, o ya estaban en la lista)") List<RiskListDataType> listed,
		@Schema(description = "Datos que este desenlace sacó de las listas al corregir el anterior") List<RiskListDataType> revoked) {

	public static RequestOutcomeResponse of(RequestOutcome outcome, List<RiskListDataType> listed,
			List<RiskListDataType> revoked) {
		return new RequestOutcomeResponse(outcome.getId(), outcome.getRequest().getId(), outcome.getOutcome(),
				outcome.getReason(), outcome.getRegisteredBy().getFullName(), outcome.getRegisteredAt(), listed, revoked);
	}

}
