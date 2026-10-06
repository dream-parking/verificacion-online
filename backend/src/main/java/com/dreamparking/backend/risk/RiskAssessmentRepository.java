package com.dreamparking.backend.risk;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, Long> {

	Optional<RiskAssessment> findByRequestIdAndCurrentTrue(UUID requestId);

	/**
	 * Marks the current assessment as superseded. Runs as a bulk update so it reaches the database before
	 * the new assessment is inserted (only one current assessment per request is allowed).
	 */
	@Modifying
	@Query("update RiskAssessment a set a.current = false where a.request.id = :requestId and a.current = true")
	int retireCurrent(UUID requestId);

}
