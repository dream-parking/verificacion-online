package com.dreamparking.backend.risklist.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.risklist.entity.RiskListEntryChange;

public interface RiskListEntryChangeRepository extends JpaRepository<RiskListEntryChange, Long> {

	/** The history of an entry, oldest first. */
	List<RiskListEntryChange> findByEntryIdOrderByIdAsc(Long entryId);

}
