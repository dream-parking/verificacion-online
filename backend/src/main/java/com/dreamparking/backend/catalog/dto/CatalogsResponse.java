package com.dreamparking.backend.catalog.dto;

import java.util.List;

/** Every catalog the onboarding flow needs, in display order. */
public record CatalogsResponse(List<CatalogItem> incomeSources, List<CatalogItem> incomeRanges,
		List<CatalogItem> transactionTypes, List<CatalogItem> monthlyAmountRanges) {
}
