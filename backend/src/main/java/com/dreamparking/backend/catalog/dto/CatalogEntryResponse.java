package com.dreamparking.backend.catalog.dto;

import java.math.BigDecimal;

import com.dreamparking.backend.catalog.entity.CatalogEntry;
import com.dreamparking.backend.catalog.entity.RangeCatalogEntry;

/** Catalog entry as maintained by administrators, active or not. Bounds are only set for range catalogs. */
public record CatalogEntryResponse(String code, String label, short sortOrder, boolean active, BigDecimal minUsd,
		BigDecimal maxUsd) {

	public static CatalogEntryResponse of(CatalogEntry entry) {
		RangeCatalogEntry range = entry instanceof RangeCatalogEntry r ? r : null;
		return new CatalogEntryResponse(entry.getCode(), entry.getLabel(), entry.getSortOrder(),
				Boolean.TRUE.equals(entry.getActive()), range == null ? null : range.getMinUsd(),
				range == null ? null : range.getMaxUsd());
	}

}
