package com.dreamparking.backend.risk.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.dreamparking.backend.risk.entity.ScoreModel;

public interface ScoreModelRepository extends JpaRepository<ScoreModel, Short> {

	/** The version in force; the database allows only one. */
	@Query("select m from ScoreModel m where m.validTo is null")
	Optional<ScoreModel> findInForce();

	/** The version in force, locked so two administrators cannot publish the next version at the same time. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from ScoreModel m where m.validTo is null")
	Optional<ScoreModel> findInForceForUpdate();

	/** Every version, newest first. */
	List<ScoreModel> findAllByOrderByVersionDesc();

}
