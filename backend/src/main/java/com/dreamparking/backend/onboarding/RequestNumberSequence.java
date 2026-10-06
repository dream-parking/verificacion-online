package com.dreamparking.backend.onboarding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Yearly counter behind request numbers (SOL-2026-00418). */
@Entity
@Table(name = "secuencia_solicitud")
public class RequestNumberSequence {

	@Id
	@Column(name = "anio")
	private Short year;

	@Column(name = "ultimo", nullable = false)
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
