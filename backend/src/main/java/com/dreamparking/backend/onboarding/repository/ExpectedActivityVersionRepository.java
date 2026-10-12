package com.dreamparking.backend.onboarding.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.onboarding.entity.ExpectedActivityVersion;
import com.dreamparking.backend.onboarding.entity.ExpectedActivityVersionId;

public interface ExpectedActivityVersionRepository extends JpaRepository<ExpectedActivityVersion, ExpectedActivityVersionId> {

	/** Versions of the request, the one in force first. */
	List<ExpectedActivityVersion> findByIdRequestIdOrderByIdVersionDesc(UUID requestId);

}
