package com.dreamparking.backend.account.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.account.dto.ChangeProfileToleranceRequest;
import com.dreamparking.backend.account.dto.ProfileToleranceResponse;
import com.dreamparking.backend.account.entity.ProfileTolerance;
import com.dreamparking.backend.account.repository.ProfileToleranceRepository;
import com.dreamparking.backend.common.exception.InvalidFieldsException;
import com.dreamparking.backend.console.entity.ConsoleUser;
import com.dreamparking.backend.console.service.AccessAuditService;
import com.dreamparking.backend.console.service.ConsoleUserService;

/**
 * Tolerance of the declared profile tracking (VDI-137). It lives in the database, so an administrator changes it from
 * the console and the next movement is compared with the new value, without a deploy. A change adds a value and never
 * edits one: the values are the log of who changed it, when, and from what to what.
 */
@Service
public class ProfileToleranceService {

	private final ProfileToleranceRepository tolerances;

	private final ConsoleUserService consoleUserService;

	private final AccessAuditService accessAuditService;

	public ProfileToleranceService(ProfileToleranceRepository tolerances, ConsoleUserService consoleUserService,
			AccessAuditService accessAuditService) {
		this.tolerances = tolerances;
		this.consoleUserService = consoleUserService;
		this.accessAuditService = accessAuditService;
	}

	/** The tolerance in force, in percent. */
	@Transactional(readOnly = true)
	public int current() {
		return tolerances.findFirstByOrderByIdDesc()
			.orElseThrow(() -> new IllegalStateException("There is no profile tolerance: migration V34 adds the first one"))
			.getTolerancePercent();
	}

	/** The tolerance in force and every change, newest first. */
	@Transactional(readOnly = true)
	public ProfileToleranceResponse log() {
		return ProfileToleranceResponse.of(tolerances.findAllByOrderByIdDesc());
	}

	/**
	 * Puts a new tolerance in force. The range (0 to 45) is checked by the request.
	 * @throws InvalidFieldsException when it is the tolerance already in force; nothing is saved
	 */
	@Transactional
	public ProfileToleranceResponse change(ChangeProfileToleranceRequest body, UUID adminId) {
		ConsoleUser admin = consoleUserService.activeUser(adminId);
		tolerances.lockForChange();
		ProfileTolerance inForce = tolerances.findFirstByOrderByIdDesc()
			.orElseThrow(() -> new IllegalStateException("There is no profile tolerance: migration V34 adds the first one"));
		int tolerancePercent = body.tolerancePercent().intValueExact();
		if (inForce.getTolerancePercent() == tolerancePercent) {
			throw new InvalidFieldsException(Map.of("tolerancePercent",
					"La tolerancia es la misma que la vigente: no hay nada que cambiar."));
		}

		ProfileTolerance saved = tolerances.saveAndFlush(new ProfileTolerance(tolerancePercent, inForce, admin));
		accessAuditService.record(admin, "PROFILE_TOLERANCE_CHANGE", "profile-tolerance", saved.getId().toString(), null);
		return log();
	}

}
