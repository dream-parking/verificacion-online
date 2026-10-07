package com.dreamparking.backend.onboarding.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.onboarding.entity.ExpectedActivity;

public interface ExpectedActivityRepository extends JpaRepository<ExpectedActivity, UUID> {

	/** Whether some expected activity already uses this monthly amount range. */
	boolean existsByMonthlyAmountRange_Code(String rangeCode);

}
