package com.dreamparking.backend.catalog.dto;

import java.math.BigDecimal;

import com.dreamparking.backend.catalog.entity.IncomeRange;
import com.dreamparking.backend.catalog.entity.IncomeSource;
import com.dreamparking.backend.catalog.entity.MonthlyAmountRange;
import com.dreamparking.backend.catalog.entity.TransactionType;

/** Option of a catalog as shown in the mobile app. Bounds (first and last amount of the range, null when open-ended) are only set for ranges. */
public record CatalogItem(String code, String label, BigDecimal minUsd, BigDecimal maxUsd) {

	public static CatalogItem of(IncomeSource source) {
		return new CatalogItem(source.getCode(), source.getLabel(), null, null);
	}

	public static CatalogItem of(IncomeRange range) {
		return new CatalogItem(range.getCode(), range.getLabel(), range.getMinUsd(), range.getMaxUsd());
	}

	public static CatalogItem of(MonthlyAmountRange range) {
		return new CatalogItem(range.getCode(), range.getLabel(), range.getMinUsd(), range.getMaxUsd());
	}

	public static CatalogItem of(TransactionType type) {
		return new CatalogItem(type.getCode(), type.getLabel(), null, null);
	}

}
