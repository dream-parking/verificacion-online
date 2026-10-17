package com.dreamparking.backend.risk.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.dreamparking.backend.risk.entity.enums.ScoreSignal;

/**
 * What one signal added to an assessment. {@code points} is null when the signal was not available (it adds nothing)
 * and {@code maxPoints} is null when the model gives the signal no weight yet.
 */
@Entity
@Table(name = "risk_assessment_signal")
public class RiskAssessmentSignal {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "assessment_id", nullable = false)
	private RiskAssessment assessment;

	@Enumerated(EnumType.STRING)
	@Column(name = "signal", nullable = false, length = 30)
	private ScoreSignal signal;

	@Column(name = "points")
	private Short points;

	@Column(name = "max_points")
	private Short maxPoints;

	@Column(name = "observation", nullable = false, columnDefinition = "text")
	private String observation;

	protected RiskAssessmentSignal() {
	}

	public RiskAssessmentSignal(RiskAssessment assessment, ScoreSignal signal, Integer points, Integer maxPoints,
			String observation) {
		this.assessment = assessment;
		this.signal = signal;
		this.points = points == null ? null : points.shortValue();
		this.maxPoints = maxPoints == null ? null : maxPoints.shortValue();
		this.observation = observation;
	}

	public ScoreSignal getSignal() {
		return signal;
	}

	public Short getPoints() {
		return points;
	}

	public Short getMaxPoints() {
		return maxPoints;
	}

	public String getObservation() {
		return observation;
	}

}
