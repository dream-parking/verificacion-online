package com.dreamparking.backend.account.dto;

import java.util.List;

/** The registered movement and the deviations from the declared profile it caused for the first time this month. */
public record RegisteredMovementResponse(MovementResponse movement,
		List<ProfileComparisonResponse.Deviation> newDeviations) {
}
