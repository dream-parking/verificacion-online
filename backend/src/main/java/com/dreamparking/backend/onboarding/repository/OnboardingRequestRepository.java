package com.dreamparking.backend.onboarding.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.dreamparking.backend.onboarding.entity.OnboardingRequest;

public interface OnboardingRequestRepository extends JpaRepository<OnboardingRequest, UUID> {

	Optional<OnboardingRequest> findByNumber(String number);

	/** Next SOL-YYYY-NNNNN number; the database function keeps a gap-free yearly counter under concurrency. */
	@Query(value = "select next_request_number(:at)", nativeQuery = true)
	String nextNumber(Instant at);

}
