package com.dreamparking.backend.risk.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.risk.entity.RiskDecision;

public interface RiskDecisionRepository extends JpaRepository<RiskDecision, Long> {

	/** The audit log of the request, the oldest decision first. */
	List<RiskDecision> findByRequestIdOrderByDecidedAtAscIdAsc(UUID requestId);

	/** The decisions of every request of the customer, the oldest first (VDI-122). */
	List<RiskDecision> findByRequestCustomerIdOrderByDecidedAtAscIdAsc(UUID customerId);

}
