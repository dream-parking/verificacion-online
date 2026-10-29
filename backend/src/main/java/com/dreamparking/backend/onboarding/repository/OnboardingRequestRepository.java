package com.dreamparking.backend.onboarding.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.dreamparking.backend.onboarding.entity.OnboardingRequest;

public interface OnboardingRequestRepository extends JpaRepository<OnboardingRequest, UUID> {

	Optional<OnboardingRequest> findByNumber(String number);

	/** The request, locked until the end of the transaction so two writers on the same request go one after the other. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from OnboardingRequest r where r.id = :id")
	Optional<OnboardingRequest> findByIdForUpdate(UUID id);

	/** Every request of the customer, the oldest first. */
	List<OnboardingRequest> findByCustomerIdOrderByStartedAtAscIdAsc(UUID customerId);

	/** Next SOL-YYYY-NNNNN number; the database function keeps a gap-free yearly counter under concurrency. */
	@Query(value = "select next_request_number(:at)", nativeQuery = true)
	String nextNumber(Instant at);

}
