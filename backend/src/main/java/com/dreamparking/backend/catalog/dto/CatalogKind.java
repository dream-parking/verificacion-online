package com.dreamparking.backend.catalog.dto;

import java.util.Arrays;

import com.dreamparking.backend.common.exception.NotFoundException;

/** Catalogs an administrator can maintain, identified in the URL by their slug. */
public enum CatalogKind {

	INCOME_SOURCES("income-sources", false),
	INCOME_RANGES("income-ranges", true),
	MONTHLY_AMOUNT_RANGES("monthly-amount-ranges", true),
	TRANSACTION_TYPES("transaction-types", false);

	private final String slug;

	private final boolean ranges;

	CatalogKind(String slug, boolean ranges) {
		this.slug = slug;
		this.ranges = ranges;
	}

	public String slug() {
		return slug;
	}

	/** Whether the entries are amount ranges with {@code minUsd}/{@code maxUsd}. */
	public boolean ranges() {
		return ranges;
	}

	public static CatalogKind fromSlug(String slug) {
		return Arrays.stream(values())
			.filter(kind -> kind.slug.equals(slug))
			.findFirst()
			.orElseThrow(() -> new NotFoundException("Unknown catalog: " + slug));
	}

}
