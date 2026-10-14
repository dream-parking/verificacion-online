package com.dreamparking.backend.account.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.account.entity.AccountMovement;

public interface AccountMovementRepository extends JpaRepository<AccountMovement, UUID> {

	boolean existsByAccountIdAndExternalId(UUID accountId, String externalId);

	/** Movements of the account in {@code [from, to)}, oldest first. */
	@EntityGraph(attributePaths = "transactionType")
	List<AccountMovement> findByAccountIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAt(
			UUID accountId, Instant from, Instant to);

}
