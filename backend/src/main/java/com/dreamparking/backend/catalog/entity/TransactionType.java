package com.dreamparking.backend.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Catalog of expected transaction types (salary payment, business collections...). */
@Entity
@Table(name = "transaction_type")
public class TransactionType implements CatalogEntry {

	@Id
	@Column(name = "code", length = 30)
	private String code;

	@Column(name = "label", nullable = false, length = 80)
	private String label;

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
