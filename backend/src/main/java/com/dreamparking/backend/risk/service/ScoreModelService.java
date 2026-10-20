package com.dreamparking.backend.risk.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.common.exception.InvalidFieldsException;
import com.dreamparking.backend.common.exception.InvalidStateException;
import com.dreamparking.backend.console.entity.ConsoleUser;
import com.dreamparking.backend.console.service.AccessAuditService;
import com.dreamparking.backend.console.service.ConsoleUserService;
import com.dreamparking.backend.risk.dto.PublishScoreModelVersionRequest;
import com.dreamparking.backend.risk.dto.ScoreModelResponse;
import com.dreamparking.backend.risk.entity.ScoreModel;
import com.dreamparking.backend.risk.repository.ScoreModelRepository;

/**
 * Versions of the score model (VDI-120). A version is never edited in place: changing a threshold publishes a new
 * version with the same weights, closes the one in force, and applies to the evaluations that come after. The
 * evaluations already made keep the version they were computed with.
 */
@Service
public class ScoreModelService {

	private final ScoreModelRepository models;

	private final ConsoleUserService consoleUserService;

	private final AccessAuditService accessAuditService;

	public ScoreModelService(ScoreModelRepository models, ConsoleUserService consoleUserService,
			AccessAuditService accessAuditService) {
		this.models = models;
		this.consoleUserService = consoleUserService;
		this.accessAuditService = accessAuditService;
	}

	/** Every version of the model, newest first: who published each one and when. */
	@Transactional(readOnly = true)
	public List<ScoreModelResponse> versions() {
		return models.findAllByOrderByVersionDesc().stream().map(ScoreModelResponse::of).toList();
	}

	/**
	 * Publishes a version with the new thresholds and the weights of the one in force.
	 * @throws InvalidFieldsException when a threshold is not valid or nothing changes; nothing is saved
	 */
	@Transactional
	public ScoreModelResponse publish(PublishScoreModelVersionRequest body, UUID adminId) {
		ConsoleUser admin = consoleUserService.activeUser(adminId);
		ScoreModel inForce = models.findInForceForUpdate()
			.orElseThrow(() -> new InvalidStateException("There is no score model in force"));
		Instant now = Instant.now();
		if (!now.isAfter(inForce.getValidFrom())) {
			throw new InvalidStateException("The score model in force starts in the future");
		}
		Map<String, String> errors = problems(body, inForce);
		if (!errors.isEmpty()) {
			throw new InvalidFieldsException(errors);
		}

		inForce.close(now);
		// Only one version may be in force: close it in the database before inserting the next one.
		models.saveAndFlush(inForce);
		ScoreModel saved = models.save(inForce.nextVersion(body.lowMax().intValueExact(), body.mediumMax().intValueExact(),
				now, admin));

		accessAuditService.record(admin, "SCORE_MODEL_PUBLISH", "score-model", "v" + saved.getVersion(), null);
		return ScoreModelResponse.of(saved);
	}

	/** What is wrong with the thresholds, by field and in Spanish; empty when they can be saved. */
	static Map<String, String> problems(PublishScoreModelVersionRequest body, ScoreModel inForce) {
		Map<String, String> errors = new LinkedHashMap<>();
		if (!isWhole(body.lowMax())) {
			errors.put("lowMax", "El fin del nivel Bajo debe ser un número entero.");
		}
		if (!isWhole(body.mediumMax())) {
			errors.put("mediumMax", "El fin del nivel Medio debe ser un número entero.");
		}
		if (!errors.isEmpty()) {
			return errors;
		}
		int low = bounded(body.lowMax());
		int medium = bounded(body.mediumMax());
		if (low < 0) {
			errors.put("lowMax", "El fin del nivel Bajo no puede ser menor que 0.");
		}
		else if (low >= medium) {
			errors.put("lowMax", "El fin del nivel Bajo debe ser menor que el fin del nivel Medio.");
		}
		if (medium >= 100) {
			errors.put("mediumMax", "El fin del nivel Medio debe ser menor que 100.");
		}
		if (errors.isEmpty() && low == inForce.getLowMax() && medium == inForce.getMediumMax()) {
			errors.put("lowMax", "Los umbrales son los mismos de la versión vigente: no hay nada que cambiar.");
		}
		return errors;
	}

	private static boolean isWhole(BigDecimal value) {
		return value.signum() == 0 || value.stripTrailingZeros().scale() <= 0;
	}

	/** The value as an int, held to -1..1000: every number outside 0..99 is invalid anyway, however large it is. */
	private static int bounded(BigDecimal value) {
		return value.max(BigDecimal.valueOf(-1)).min(BigDecimal.valueOf(1000)).intValue();
	}

}
