package com.dreamparking.backend.onboarding;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OnboardingSessionRepository extends JpaRepository<OnboardingSession, UUID> {

	List<OnboardingSession> findByRequestIdOrderByStartedAt(UUID requestId);

}
