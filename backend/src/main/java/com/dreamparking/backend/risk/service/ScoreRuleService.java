package com.dreamparking.backend.risk.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.common.exception.InvalidInputException;
import com.dreamparking.backend.common.exception.InvalidStateException;
import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.console.entity.ConsoleUser;
import com.dreamparking.backend.console.service.AccessAuditService;
import com.dreamparking.backend.console.service.ConsoleUserService;
import com.dreamparking.backend.risk.dto.PublishScoreRuleVersionRequest;
import com.dreamparking.backend.risk.dto.ScoreRuleResponse;
import com.dreamparking.backend.risk.entity.ScoreRule;
import com.dreamparking.backend.risk.entity.enums.RuleStatus;
import com.dreamparking.backend.risk.repository.ScoreRuleRepository;

/**
 * Versions of the score rules. A rule is never edited in place: a change publishes a new version, so every risk
 * assessment keeps pointing to the exact version (and threshold) it was computed with.
 */
@Service
public class ScoreRuleService {

	private final ScoreRuleRepository rules;

	private final ConsoleUserService consoleUserService;

	private final AccessAuditService accessAuditService;

	public ScoreRuleService(ScoreRuleRepository rules, ConsoleUserService consoleUserService,
			AccessAuditService accessAuditService) {
		this.rules = rules;
		this.consoleUserService = consoleUserService;
		this.accessAuditService = accessAuditService;
	}

	/** Every version of the rule, newest first. */
	@Transactional(readOnly = true)
	public List<ScoreRuleResponse> versions(String code) {
		List<ScoreRule> versions = rules.findByCodeOrderByRuleVersionDesc(code);
		if (versions.isEmpty()) {
			throw new NotFoundException("Score rule not found: " + code);
		}
		return versions.stream().map(ScoreRuleResponse::of).toList();
	}

	/** Publishes a new version copied from the latest one; PROVISIONAL and CONFIRMED retire the version in force. */
	@Transactional
	public ScoreRuleResponse publish(String code, PublishScoreRuleVersionRequest body, UUID adminId) {
		ConsoleUser admin = consoleUserService.activeUser(adminId);
		if (body.status() == RuleStatus.RETIRED) {
			throw new InvalidInputException("A new version cannot be RETIRED; publish it as DRAFT, PROVISIONAL or CONFIRMED");
		}
		List<ScoreRule> versions = rules.findByCodeOrderByRuleVersionDesc(code);
		if (versions.isEmpty()) {
			throw new NotFoundException("Score rule not found: " + code);
		}
		ScoreRule latest = versions.get(0);
		Instant now = Instant.now();

		boolean appliesNow = body.status() == RuleStatus.PROVISIONAL || body.status() == RuleStatus.CONFIRMED;
		if (appliesNow) {
			rules.findInForce(code).ifPresent(inForce -> {
				if (!now.isAfter(inForce.getValidFrom())) {
					throw new InvalidStateException("The version in force of " + code + " starts in the future");
				}
				inForce.setValidTo(now);
				// Only one version per code may be in force: retire it in the database before inserting the new one.
				rules.saveAndFlush(inForce);
			});
		}

		ScoreRule version = new ScoreRule();
		version.setCode(latest.getCode());
		version.setRuleVersion((short) (latest.getRuleVersion() + 1));
		version.setName(latest.getName());
		version.setDescription(latest.getDescription());
		version.setEvaluatedField(latest.getEvaluatedField());
		version.setOperator(latest.getOperator());
		version.setCurrency(latest.getCurrency());
		version.setResultIfMatched(latest.getResultIfMatched());
		version.setResultIfNotMatched(latest.getResultIfNotMatched());
		version.setPriority(latest.getPriority());
		version.setThreshold(body.threshold());
		version.setStatus(body.status());
		version.setStatusNote(body.statusNote());
		version.setValidFrom(now);
		version.setUpdatedBy(admin);
		ScoreRule saved = rules.save(version);

		accessAuditService.record(admin, "SCORE_RULE_PUBLISH", "score-rule", code + " v" + saved.getRuleVersion(), null);
		return ScoreRuleResponse.of(saved);
	}

}
