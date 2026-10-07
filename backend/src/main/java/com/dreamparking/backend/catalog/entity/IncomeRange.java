package com.dreamparking.backend.catalog.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Catalog of monthly income ranges in USD; a null bound means open-ended. */
@Entity
@Table(name = "income_range")
public class IncomeRange {

	@Id
	@Column(name = "code", length = 30)
	private String code;

	@Column(name = "label", nullable = false, length = 80)
	private String label;

	@Column(name = "min_usd", precision = 14, scale = 2)
	private BigDecimal minUsd;

	@Column(name = "max_usd", precision = 14, scale = 2)
	private BigDecimal maxUsd;

	@Column(name = "sort_order", nullable = false)
	private Short sortOrder = (short) 0;

	@Column(name = "active", nullable = false)
	private Boolean active = true;

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public BigDecimal getMinUsd() {
		return minUsd;
	}

	public void setMinUsd(BigDecimal minUsd) {
		this.minUsd = minUsd;
	}

	public BigDecimal getMaxUsd() {
		return maxUsd;
	}

	public void setMaxUsd(BigDecimal maxUsd) {
		this.maxUsd = maxUsd;
	}

	public Short getSortOrder() {
		return sortOrder;
	}

	public void setSortOrder(Short sortOrder) {
		this.sortOrder = sortOrder;
	}

	public Boolean getActive() {
		return active;
	}

	public void setActive(Boolean active) {
		this.active = active;
	}

}
