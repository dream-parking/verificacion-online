package com.dreamparking.backend.account.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.dreamparking.backend.account.entity.ProfileTolerance;

public interface ProfileToleranceRepository extends JpaRepository<ProfileTolerance, Long> {

	/** The tolerance in force: the newest value. */
	Optional<ProfileTolerance> findFirstByOrderByIdDesc();

	/**
	 * Holds new changes until the transaction ends, so two administrators changing it at the same time do not both
	 * take the same value as the previous one. Reading the tolerance is not blocked.
	 */
	@Modifying
	@Query(value = "LOCK TABLE profile_tolerance IN SHARE ROW EXCLUSIVE MODE", nativeQuery = true)
	void lockForChange();

	/** Every value, newest first. */
	List<ProfileTolerance> findAllByOrderByIdDesc();

}
