package com.dreamparking.backend.catalog.entity;

import java.math.BigDecimal;

/** Catalog entry that is an amount range in USD; a null bound means open-ended. */
public interface RangeCatalogEntry extends CatalogEntry {

	BigDecimal getMinUsd();

	void setMinUsd(BigDecimal minUsd);

	BigDecimal getMaxUsd();

	void setMaxUsd(BigDecimal maxUsd);

}
