package com.dreamparking.backend.onboarding;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OnboardingRequestRepository extends JpaRepository<OnboardingRequest, UUID> {

	Optional<OnboardingRequest> findByNumber(String number);

}
