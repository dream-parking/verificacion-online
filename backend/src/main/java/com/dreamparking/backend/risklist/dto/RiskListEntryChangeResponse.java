package com.dreamparking.backend.risklist.dto;

import java.time.Instant;

import com.dreamparking.backend.risklist.entity.RiskListEntryChange;
import com.dreamparking.backend.risklist.entity.enums.RiskListChangeAction;

/** One change of a risk list entry: what, who, when and why. */
public record RiskListEntryChangeResponse(RiskListChangeAction action, String changedBy, Instant changedAt,
		String reason) {

	public static RiskListEntryChangeResponse of(RiskListEntryChange change) {
		return new RiskListEntryChangeResponse(change.getAction(), change.getChangedBy().getFullName(),
				change.getChangedAt(), change.getReason());
	}

}
