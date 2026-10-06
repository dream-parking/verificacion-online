package com.dreamparking.backend.onboarding.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Yearly counter behind request numbers (SOL-2026-00418). */
@Entity
@Table(name = "request_number_sequence")
public class RequestNumberSequence {

	@Id
	@Column(name = "year")
	private Short year;

	@Column(name = "last_number", nullable = false)
	private Integer lastNumber = 0;

	public Short getYear() {
		return year;
	}

	public void setYear(Short year) {
		this.year = year;
	}

	public Integer getLastNumber() {
		return lastNumber;
	}

	public void setLastNumber(Integer lastNumber) {
		this.lastNumber = lastNumber;
	}

}
