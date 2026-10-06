package com.dreamparking.backend.onboarding.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.onboarding.entity.OnboardingSession;

public interface OnboardingSessionRepository extends JpaRepository<OnboardingSession, UUID> {

	List<OnboardingSession> findByRequestIdOrderByStartedAt(UUID requestId);

}
