package com.dreamparking.backend.onboarding.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.onboarding.entity.RequestStep;
import com.dreamparking.backend.onboarding.entity.RequestStepId;

public interface RequestStepRepository extends JpaRepository<RequestStep, RequestStepId> {

	List<RequestStep> findByRequestIdOrderByStartedAt(UUID requestId);

}
