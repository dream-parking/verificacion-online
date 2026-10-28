package com.dreamparking.backend.risklist.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.dreamparking.backend.risklist.entity.RiskListEntry;

public interface RiskListEntryRepository extends JpaRepository<RiskListEntry, Long> {

	/** The active entries with any of these hashes, with their list. */
	@Query("select e from RiskListEntry e join fetch e.list where e.active = true and e.valueHash in :hashes")
	List<RiskListEntry> findActiveByHashes(Collection<String> hashes);

	boolean existsByListIdAndValueHashAndActiveTrue(Short listId, String valueHash);

	/** Every entry of a list, newest first. */
	List<RiskListEntry> findByListCodeOrderByIdDesc(String listCode);

}
