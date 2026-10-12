package com.dreamparking.backend.risk.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.risk.dto.ScoreRuleResponse;
import com.dreamparking.backend.risk.repository.ScoreRuleRepository;

@RestController
@RequestMapping("/api/console/score-rules")
@Tag(name = "Consola · Reglas", description = "Reglas de score vigentes (solo lectura)")
@SecurityRequirement(name = "bearerAuth")
public class ScoreRuleController {

	private final ScoreRuleRepository rules;

	public ScoreRuleController(ScoreRuleRepository rules) {
		this.rules = rules;
	}

	@Operation(summary = "Reglas de score vigentes y su umbral",
			description = "R-01 (monto mensual bajo → riesgo bajo) está retirada: el score lo calcula el modelo de "
					+ "`/api/console/score-model`. Esta lista queda vacía mientras no haya una versión vigente.")
	@GetMapping
	@Transactional(readOnly = true)
	public List<ScoreRuleResponse> inForce() {
		return rules.findAllInForce().stream().map(ScoreRuleResponse::of).toList();
	}

}
