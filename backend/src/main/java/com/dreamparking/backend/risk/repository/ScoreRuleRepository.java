package com.dreamparking.backend.risk.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.dreamparking.backend.risk.entity.ScoreRule;
import com.dreamparking.backend.risk.entity.enums.RuleStatus;

public interface ScoreRuleRepository extends JpaRepository<ScoreRule, Integer> {

	/** Version of the rule currently in force (not retired and not a draft). */
	@Query("from ScoreRule r where r.code = :code and r.validTo is null and r.status in :statuses")
	Optional<ScoreRule> findInForce(String code, Collection<RuleStatus> statuses);

	/** Every rule version in force, in evaluation order. */
	@Query("from ScoreRule r where r.validTo is null and r.status in :statuses order by r.priority, r.code")
	List<ScoreRule> findAllInForce(Collection<RuleStatus> statuses);

	default List<ScoreRule> findAllInForce() {
		return findAllInForce(List.of(RuleStatus.PROVISIONAL, RuleStatus.CONFIRMED));
	}

	default Optional<ScoreRule> findInForce(String code) {
		return findInForce(code, List.of(RuleStatus.PROVISIONAL, RuleStatus.CONFIRMED));
	}

	/** Every version of a rule, newest first. */
	List<ScoreRule> findByCodeOrderByRuleVersionDesc(String code);

}
