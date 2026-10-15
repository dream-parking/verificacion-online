package com.dreamparking.backend.onboarding.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.onboarding.entity.ExpectedActivityVersion;

public interface ExpectedActivityVersionRepository extends JpaRepository<ExpectedActivityVersion, Long> {

	/** The version in force: the highest one. */
	Optional<ExpectedActivityVersion> findFirstByRequest_IdOrderByVersionDesc(UUID requestId);

	/** Every version, the oldest first. */
	List<ExpectedActivityVersion> findByRequest_IdOrderByVersionAsc(UUID requestId);

}
