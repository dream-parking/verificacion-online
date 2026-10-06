package com.dreamparking.backend.onboarding;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpectedActivityRepository extends JpaRepository<ExpectedActivity, UUID> {

}
