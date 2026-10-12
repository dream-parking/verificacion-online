package com.dreamparking.backend.risk.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.dreamparking.backend.risk.entity.ScoreModel;

public interface ScoreModelRepository extends JpaRepository<ScoreModel, Short> {

	/** The version in force; the database allows only one. */
	@Query("select m from ScoreModel m where m.validTo is null")
	Optional<ScoreModel> findInForce();

}
