package com.dreamparking.backend.outcome.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.dreamparking.backend.outcome.entity.RequestOutcomeEntry;
import com.dreamparking.backend.risklist.entity.RiskListEntry;

public interface RequestOutcomeEntryRepository extends JpaRepository<RequestOutcomeEntry, Long> {

	/** What the outcomes of a request did to the lists, with the entry. */
	@Query("select l from RequestOutcomeEntry l join fetch l.entry where l.outcome.request.id = :requestId")
	List<RequestOutcomeEntry> findByRequestId(UUID requestId);

	/** The entries that the outcomes of a request created and that are still active. */
	@Query("""
			select l.entry from RequestOutcomeEntry l
			where l.outcome.request.id = :requestId and l.link = com.dreamparking.backend.outcome.entity.enums.OutcomeEntryLink.LISTED
			  and l.entry.active = true order by l.entry.id""")
	List<RiskListEntry> findActiveListedByRequestId(UUID requestId);

}
