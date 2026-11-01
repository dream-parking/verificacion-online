package com.dreamparking.backend.outcome.repository;

import java.util.List;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.outcome.entity.RequestOutcome;

public interface RequestOutcomeRepository extends JpaRepository<RequestOutcome, Long> {

	/** The outcomes of a request, the oldest first; the last one is the current one. */
	List<RequestOutcome> findByRequestIdOrderByIdAsc(UUID requestId);

}
