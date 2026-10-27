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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;

import com.dreamparking.backend.console.entity.ConsoleUser;
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

	/** Console user who published this version; null for the versions published by a migration. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "published_by")
	private ConsoleUser publishedBy;

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

	/** When this version stopped being in force; null while it is. */
	public Instant getValidTo() {
		return validTo;
	}

	public ConsoleUser getPublishedBy() {
		return publishedBy;
	}

	/** Stops this version from applying to the evaluations that come after {@code now}. */
	public void close(Instant now) {
		this.validTo = now;
	}

	/** The version that replaces this one: the same weights, new ranges, in force from {@code now}. */
	public ScoreModel nextVersion(int newLowMax, int newMediumMax, Instant now, ConsoleUser publisher) {
		ScoreModel next = new ScoreModel();
		next.version = (short) (version + 1);
		next.name = "Modelo de score v" + next.version;
		next.lowMax = (short) newLowMax;
		next.mediumMax = (short) newMediumMax;
		next.validFrom = now;
		next.publishedBy = publisher;
		next.weights = new EnumMap<>(weights);
		return next;
	}

	/** Points the signal can add at most, or {@code null} when the model gives it no weight. */
	public Integer weightOf(ScoreSignal signal) {
		Short weight = weights.get(signal);
		return weight == null ? null : weight.intValue();
	}

	/** Score above which a submitted request is held for enhanced review: where the high range begins (VDI-117). */
	public short reviewThreshold() {
		return mediumMax;
	}

	public boolean requiresEnhancedReview(int score) {
		return score > mediumMax;
	}

	/** Risk level of a score: low up to {@code lowMax}, medium up to {@code mediumMax}, high above that. */
	public RiskLevel levelOf(int score) {
		if (score <= lowMax) {
			return RiskLevel.LOW;
		}
		return score <= mediumMax ? RiskLevel.MEDIUM : RiskLevel.HIGH;
	}

}
