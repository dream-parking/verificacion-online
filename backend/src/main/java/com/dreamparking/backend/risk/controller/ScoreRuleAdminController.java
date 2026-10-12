package com.dreamparking.backend.risk.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.common.validation.Code;
import com.dreamparking.backend.risk.dto.PublishScoreRuleVersionRequest;
import com.dreamparking.backend.risk.dto.ScoreRuleResponse;
import com.dreamparking.backend.risk.service.ScoreRuleService;
import com.dreamparking.backend.security.AuthenticatedUser;

/** Version history of the score rules and publication of new versions. Administrators only. */
@RestController
@RequestMapping("/api/console/admin/score-rules")
@Tag(name = "Consola · Administración de reglas", description = "Versiones de las reglas de score (administradores)")
@SecurityRequirement(name = "bearerAuth")
public class ScoreRuleAdminController {

	private final ScoreRuleService scoreRuleService;

	public ScoreRuleAdminController(ScoreRuleService scoreRuleService) {
		this.scoreRuleService = scoreRuleService;
	}

	@ApiResponse(responseCode = "200", description = "Versiones, de la más reciente a la más antigua")
	@Operation(summary = "Historial de versiones de una regla", description = "La vigente es la que no tiene `validTo` y no está en borrador.")
	@ApiResponse(responseCode = "403", description = "Solo administradores", content = @Content)
	@ApiResponse(responseCode = "404", description = "La regla no existe", content = @Content)
	@GetMapping("/{code}/versions")
	public List<ScoreRuleResponse> versions(@PathVariable @Size(max = 40) @Code String code) {
		return scoreRuleService.versions(code);
	}

	@ApiResponse(responseCode = "201", description = "Versión publicada")
	@Operation(summary = "Publica una versión nueva de una regla (por ejemplo, otro umbral)",
			description = "Copia la lógica de la última versión con el umbral y el estado nuevos. PROVISIONAL o CONFIRMED "
					+ "la aplican desde ahora y retiran la vigente; DRAFT solo la guarda. Las evaluaciones ya hechas "
					+ "conservan la versión con la que se calcularon.")
	@ApiResponse(responseCode = "400", description = "Datos inválidos o estado RETIRED", content = @Content)
	@ApiResponse(responseCode = "403", description = "Solo administradores", content = @Content)
	@ApiResponse(responseCode = "404", description = "La regla no existe", content = @Content)
	@ApiResponse(responseCode = "409", description = "La versión vigente empieza en el futuro", content = @Content)
	@PostMapping("/{code}/versions")
	@ResponseStatus(HttpStatus.CREATED)
	public ScoreRuleResponse publish(@PathVariable @Size(max = 40) @Code String code,
			@Valid @RequestBody PublishScoreRuleVersionRequest body,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser admin) {
		return scoreRuleService.publish(code, body, admin.id());
	}

}
