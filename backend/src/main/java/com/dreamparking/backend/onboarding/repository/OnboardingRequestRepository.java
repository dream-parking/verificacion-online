package com.dreamparking.backend.onboarding.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.onboarding.entity.OnboardingRequest;

public interface OnboardingRequestRepository extends JpaRepository<OnboardingRequest, UUID> {

	Optional<OnboardingRequest> findByNumber(String number);

}
