package com.dreamparking.backend.alert.dto;

import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.dreamparking.backend.alert.entity.enums.AlertCriticality;
import com.dreamparking.backend.common.validation.Code;
import com.dreamparking.backend.common.validation.FreeText;

/** New alert on an account. Without {@code criticality}, the alert type's default is used. */
public record RaiseAlertRequest(@NotNull UUID accountId, @NotBlank @Size(max = 40) @Code String typeCode,
		AlertCriticality criticality, @NotBlank @Size(max = 250) @FreeText String reason,
		@Size(max = 50) Map<String, Object> evidence) {
}
