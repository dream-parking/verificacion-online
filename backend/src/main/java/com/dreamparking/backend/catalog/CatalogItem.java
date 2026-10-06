package com.dreamparking.backend.catalog;

import java.math.BigDecimal;

/** Option of a catalog as shown in the mobile app. Bounds (first and last amount of the range, null when open-ended) are only set for ranges. */
public record CatalogItem(String code, String label, BigDecimal minUsd, BigDecimal maxUsd) {

	static CatalogItem of(IncomeSource source) {
		return new CatalogItem(source.getCode(), source.getLabel(), null, null);
	}

	static CatalogItem of(IncomeRange range) {
		return new CatalogItem(range.getCode(), range.getLabel(), range.getMinUsd(), range.getMaxUsd());
	}

	static CatalogItem of(MonthlyAmountRange range) {
		return new CatalogItem(range.getCode(), range.getLabel(), range.getMinUsd(), range.getMaxUsd());
	}

	static CatalogItem of(TransactionType type) {
		return new CatalogItem(type.getCode(), type.getLabel(), null, null);
	}

}
