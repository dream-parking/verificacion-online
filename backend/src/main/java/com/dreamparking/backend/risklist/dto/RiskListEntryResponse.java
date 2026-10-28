package com.dreamparking.backend.risklist.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dreamparking.backend.risklist.entity.RiskListEntry;
import com.dreamparking.backend.risklist.entity.enums.RiskListDataType;

/**
 * An entry of a risk list. It never carries the value, nor its hash: they are personal data. {@code deactivatedBy},
 * {@code deactivatedAt} and {@code deactivationReason} are null while the entry is active.
 */
public record RiskListEntryResponse(Long id, RiskListResponse list, RiskListDataType dataType, String reason,
		@Schema(description = "Una entrada inactiva ya no coincide, pero queda en el historial") boolean active,
		String addedBy, Instant addedAt, String deactivatedBy, Instant deactivatedAt, String deactivationReason) {

	public static RiskListEntryResponse of(RiskListEntry entry) {
		return new RiskListEntryResponse(entry.getId(), RiskListResponse.of(entry.getList()), entry.getDataType(),
				entry.getReason(), entry.isActive(), entry.getAddedBy().getFullName(), entry.getAddedAt(),
				entry.getDeactivatedBy() == null ? null : entry.getDeactivatedBy().getFullName(),
				entry.getDeactivatedAt(), entry.getDeactivationReason());
	}

}
