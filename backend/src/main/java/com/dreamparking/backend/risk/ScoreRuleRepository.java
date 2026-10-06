package com.dreamparking.backend.risk;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ScoreRuleRepository extends JpaRepository<ScoreRule, Integer> {

	/** Version of the rule currently in force (not retired and not a draft). */
	@Query("from ScoreRule r where r.code = :code and r.validTo is null and r.status in :statuses")
	Optional<ScoreRule> findInForce(String code, Collection<RuleStatus> statuses);

	default Optional<ScoreRule> findInForce(String code) {
		return findInForce(code, List.of(RuleStatus.PROVISIONAL, RuleStatus.CONFIRMED));
	}

}
