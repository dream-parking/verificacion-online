package com.dreamparking.backend.alert.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Closes an alert. {@code resolution} is a short code such as FALSO_POSITIVO or ESCALADA_ROS. */
public record CloseAlertRequest(@NotNull UUID userId, @NotBlank @Size(max = 30) String resolution, String comment) {
}
