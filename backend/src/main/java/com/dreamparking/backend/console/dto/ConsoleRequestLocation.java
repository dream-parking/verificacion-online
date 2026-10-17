package com.dreamparking.backend.console.dto;

import java.math.BigDecimal;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dreamparking.backend.onboarding.entity.enums.LocationStatus;
import com.dreamparking.backend.onboarding.entity.enums.RequestStatus;
import com.dreamparking.backend.risk.entity.enums.RiskLevel;

/**
 * Where a request was made from, for the console map (VDI-109): the location the server resolved from the IP of the
 * last session. {@code locationStatus} is null when the app never sent the signals; {@code latitude} and
 * {@code longitude} are only filled in when it is {@code AVAILABLE}.
 */
public record ConsoleRequestLocation(UUID id, String number, String name, RiskLevel riskLevel, RequestStatus status,
		String ip, LocationStatus locationStatus, BigDecimal latitude, BigDecimal longitude,
		@Schema(description = "Ciudad según la IP", example = "San Salvador") String city,
		@Schema(description = "Departamento o región según la IP", example = "San Salvador") String regionName,
		@Schema(description = "País según la IP", example = "El Salvador") String country,
		@Schema(description = "Ubicación aproximada; el texto de la app solo si el servidor no resolvió la IP") String approximateLocation) {
}
