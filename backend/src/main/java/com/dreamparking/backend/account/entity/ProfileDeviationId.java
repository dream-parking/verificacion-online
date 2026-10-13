package com.dreamparking.backend.account.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import com.dreamparking.backend.account.entity.enums.ProfileDeviationType;

/** Primary key of {@link ProfileDeviation}: one row per account, month and kind of deviation. */
@Embeddable
public class ProfileDeviationId implements Serializable {

	@Column(name = "account_id")
	private UUID accountId;

	/** First day of the month. */
	@Column(name = "period")
	private LocalDate period;

	@Enumerated(EnumType.STRING)
	@Column(name = "deviation", length = 40)
	private ProfileDeviationType deviation;

	protected ProfileDeviationId() {
	}

	public ProfileDeviationId(UUID accountId, LocalDate period, ProfileDeviationType deviation) {
		this.accountId = accountId;
		this.period = period;
		this.deviation = deviation;
	}

	public UUID getAccountId() {
		return accountId;
	}

	public LocalDate getPeriod() {
		return period;
	}

	public ProfileDeviationType getDeviation() {
		return deviation;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof ProfileDeviationId other && Objects.equals(accountId, other.accountId)
				&& Objects.equals(period, other.period) && deviation == other.deviation;
	}

	@Override
	public int hashCode() {
		return Objects.hash(accountId, period, deviation);
	}

}
