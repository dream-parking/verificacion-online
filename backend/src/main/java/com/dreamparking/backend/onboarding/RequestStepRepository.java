package com.dreamparking.backend.onboarding;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestStepRepository extends JpaRepository<RequestStep, RequestStepId> {

	List<RequestStep> findByRequestIdOrderByStartedAt(UUID requestId);

}
