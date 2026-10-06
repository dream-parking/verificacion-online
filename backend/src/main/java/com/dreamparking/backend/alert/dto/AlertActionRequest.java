package com.dreamparking.backend.alert.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Console user taking or reviewing an alert, with an optional comment for the history. */
public record AlertActionRequest(@NotNull UUID userId, String comment) {
}
