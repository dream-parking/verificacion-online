package com.dreamparking.backend.onboarding.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.onboarding.entity.IncomeDeclaration;

public interface IncomeDeclarationRepository extends JpaRepository<IncomeDeclaration, UUID> {

	/** Whether some declaration already uses this income range (its bounds are then part of a KYC file). */
	boolean existsByRange_Code(String rangeCode);

}
