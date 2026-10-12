package com.dreamparking.backend.risk.entity;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;

import com.dreamparking.backend.risk.entity.enums.RiskLevel;
import com.dreamparking.backend.risk.entity.enums.ScoreSignal;

/**
 * Version of the score model: the points each signal can add (its weight) and where the score ranges end. A version is
 * never edited in place, so an assessment can always be explained with the version that produced it.
 */
@Entity
@Table(name = "score_model")
public class ScoreModel {

	@Id
	@Column(name = "version")
	private Short version;

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Column(name = "low_max", nullable = false)
	private Short lowMax;

	@Column(name = "medium_max", nullable = false)
	private Short mediumMax;

	@Column(name = "valid_from", nullable = false)
	private Instant validFrom;

	@Column(name = "valid_to")
	private Instant validTo;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "score_model_weight", joinColumns = @JoinColumn(name = "model_version"))
	@MapKeyColumn(name = "signal")
	@MapKeyEnumerated(EnumType.STRING)
	@Column(name = "max_points", nullable = false)
	private Map<ScoreSignal, Short> weights = new EnumMap<>(ScoreSignal.class);

	public Short getVersion() {
		return version;
	}

	public String getName() {
		return name;
	}

	public Short getLowMax() {
		return lowMax;
	}

	public Short getMediumMax() {
		return mediumMax;
	}

	public Instant getValidFrom() {
		return validFrom;
	}

	public Instant getValidTo() {
		return validTo;
	}

	/** Points the signal can add at most, or {@code null} when the model gives it no weight. */
	public Integer weightOf(ScoreSignal signal) {
		Short weight = weights.get(signal);
		return weight == null ? null : weight.intValue();
	}

	/** Risk level of a score: low up to {@code lowMax}, medium up to {@code mediumMax}, high above that. */
	public RiskLevel levelOf(int score) {
		if (score <= lowMax) {
			return RiskLevel.LOW;
		}
		return score <= mediumMax ? RiskLevel.MEDIUM : RiskLevel.HIGH;
	}

}
