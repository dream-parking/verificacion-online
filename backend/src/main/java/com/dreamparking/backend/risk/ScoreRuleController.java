package com.dreamparking.backend.risk;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/console/score-rules")
@Tag(name = "Consola · Reglas", description = "Reglas de score vigentes (solo lectura)")
public class ScoreRuleController {

	private final ScoreRuleRepository rules;

	public ScoreRuleController(ScoreRuleRepository rules) {
		this.rules = rules;
	}

	@Operation(summary = "Reglas de score vigentes y su umbral",
			description = "Hoy solo R-01 (monto mensual bajo → riesgo bajo). Los umbrales no se editan en este sprint.")
	@GetMapping
	@Transactional(readOnly = true)
	public List<ScoreRuleResponse> inForce() {
		return rules.findAllInForce().stream().map(ScoreRuleResponse::of).toList();
	}

}
