package com.dreamparking.backend.risk.controller;

import java.util.List;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.risk.dto.PublishScoreModelVersionRequest;
import com.dreamparking.backend.risk.dto.ScoreModelResponse;
import com.dreamparking.backend.risk.service.ScoreModelService;
import com.dreamparking.backend.security.AuthenticatedUser;

/** Version history of the score model and publication of new thresholds. Administrators only. */
@RestController
@RequestMapping("/api/console/admin/score-model/versions")
@Tag(name = "Consola · Administración del modelo de score", description = "Umbrales y versiones del modelo de score (administradores)")
@SecurityRequirement(name = "bearerAuth")
public class ScoreModelAdminController {

	private final ScoreModelService scoreModelService;

	public ScoreModelAdminController(ScoreModelService scoreModelService) {
		this.scoreModelService = scoreModelService;
	}

	@ApiResponse(responseCode = "200", description = "Versiones, de la más reciente a la más antigua")
	@Operation(summary = "Historial de versiones del modelo de score",
			description = "Cada versión trae sus umbrales, quién la publicó (`publishedBy`, vacío si la publicó una migración) "
					+ "y cuándo (`validFrom`). La vigente es la que no tiene `validTo` (`inForce: true`).")
	@ApiResponse(responseCode = "403", description = "Solo administradores", content = @Content)
	@GetMapping
	public List<ScoreModelResponse> versions() {
		return scoreModelService.versions();
	}

	@ApiResponse(responseCode = "201", description = "Versión publicada")
	@Operation(summary = "Publica una versión nueva del modelo de score con otros umbrales",
			description = "Copia los pesos de la versión vigente, la cierra y aplica los umbrales nuevos a las evaluaciones "
					+ "siguientes, sin desplegar código. Las evaluaciones ya hechas conservan la versión con la que se "
					+ "calcularon. Los umbrales deben ser enteros con 0 ≤ `lowMax` < `mediumMax` < 100.")
	@ApiResponse(responseCode = "400", description = "Umbral inválido o sin cambios: `errors` dice por qué, en español, y no se guarda nada",
			content = @Content)
	@ApiResponse(responseCode = "403", description = "Solo administradores", content = @Content)
	@ApiResponse(responseCode = "409", description = "No hay un modelo vigente, o el vigente empieza en el futuro", content = @Content)
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ScoreModelResponse publish(@Valid @RequestBody PublishScoreModelVersionRequest body,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser admin) {
		return scoreModelService.publish(body, admin.id());
	}

}
