package com.dreamparking.backend.risk.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.risk.dto.ScoreModelResponse;
import com.dreamparking.backend.risk.repository.ScoreModelRepository;

@RestController
@RequestMapping("/api/console/score-model")
@Tag(name = "Consola · Modelo de score", description = "Modelo de ponderación del score (solo lectura)")
@SecurityRequirement(name = "bearerAuth")
public class ScoreModelController {

	private final ScoreModelRepository models;

	public ScoreModelController(ScoreModelRepository models) {
		this.models = models;
	}

	@Operation(summary = "Modelo de score vigente",
			description = "Los rangos del score (bajo, medio, alto) y los puntos máximos de cada señal. "
					+ "Las señales sin peso (RNPN, prueba de vida, listas internas) llegan con maxPoints nulo.")
	@GetMapping
	@Transactional(readOnly = true)
	public ScoreModelResponse inForce() {
		return models.findInForce()
			.map(ScoreModelResponse::of)
			.orElseThrow(() -> new NotFoundException("There is no score model in force"));
	}

}
