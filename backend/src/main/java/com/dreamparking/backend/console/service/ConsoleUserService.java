package com.dreamparking.backend.console.service;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.common.exception.InvalidInputException;
import com.dreamparking.backend.common.exception.InvalidStateException;
import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.console.dto.ConsoleUserResponse;
import com.dreamparking.backend.console.dto.CreateUserRequest;
import com.dreamparking.backend.console.dto.UpdateUserRequest;
import com.dreamparking.backend.console.entity.ConsoleUser;
import com.dreamparking.backend.console.entity.enums.ConsoleRole;
import com.dreamparking.backend.console.repository.ConsoleUserRepository;

/** Management of console users (administrators only) and the password changes. */
@Service
@Transactional
public class ConsoleUserService {

	private final ConsoleUserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final Clock clock;

	public ConsoleUserService(ConsoleUserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<ConsoleUserResponse> list(boolean includeInactive) {
		return users.findAll(Sort.by("fullName"))
			.stream()
			.filter(user -> includeInactive || Boolean.TRUE.equals(user.getActive()))
			.map(ConsoleUserResponse::of)
			.toList();
	}

	public ConsoleUserResponse create(CreateUserRequest body) {
		String email = body.email().trim().toLowerCase(Locale.ROOT);
		if (users.existsByEmailIgnoreCase(email)) {
			throw new InvalidStateException("Ya existe un usuario con el correo " + email);
		}
		PasswordPolicy.check(body.initialPassword(), email);

		ConsoleUser user = new ConsoleUser();
		user.setEmail(email);
		user.setFullName(body.fullName().trim());
		user.setJobTitle(body.jobTitle().trim());
		user.setInitials(initialsOf(body.fullName()));
		user.setRole(body.role());
		user.setActive(true);
		user.changePasswordHash(passwordEncoder.encode(body.initialPassword()), clock.instant());
		return ConsoleUserResponse.of(users.save(user));
	}

	/** Edits name, job title, role and status; the last active administrator cannot be demoted or deactivated. */
	public ConsoleUserResponse update(UUID id, UpdateUserRequest body) {
		ConsoleUser user = find(id);
		boolean losesAdminAccess = user.getRole() == ConsoleRole.ADMIN && Boolean.TRUE.equals(user.getActive())
				&& (body.role() != ConsoleRole.ADMIN || !body.active());
		if (losesAdminAccess && users.countByRoleAndActiveTrue(ConsoleRole.ADMIN) <= 1) {
			throw new InvalidStateException("Debe quedar al menos un administrador activo");
		}
		user.setFullName(body.fullName().trim());
		user.setJobTitle(body.jobTitle().trim());
		user.setInitials(initialsOf(body.fullName()));
		user.setRole(body.role());
		user.setActive(body.active());
		return ConsoleUserResponse.of(users.save(user));
	}

	/** An administrator sets a new password for someone else (forgotten or first password). */
	public void resetPassword(UUID id, String newPassword) {
		ConsoleUser user = find(id);
		PasswordPolicy.check(newPassword, user.getEmail());
		user.changePasswordHash(passwordEncoder.encode(newPassword), clock.instant());
	}

	/** A user changes their own password; the current one must be right. */
	public void changeOwnPassword(UUID id, String currentPassword, String newPassword) {
		ConsoleUser user = find(id);
		if (user.getPasswordHash() == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
			throw new InvalidInputException("La contraseña actual no es correcta");
		}
		if (currentPassword.equals(newPassword)) {
			throw new InvalidInputException("La contraseña nueva debe ser distinta de la actual");
		}
		PasswordPolicy.check(newPassword, user.getEmail());
		user.changePasswordHash(passwordEncoder.encode(newPassword), clock.instant());
	}

	@Transactional(readOnly = true)
	public ConsoleUserResponse get(UUID id) {
		return ConsoleUserResponse.of(find(id));
	}

	/** Active user acting in the console; inactive users cannot take or work alerts. */
	@Transactional(readOnly = true)
	public ConsoleUser activeUser(UUID id) {
		ConsoleUser user = find(id);
		if (!Boolean.TRUE.equals(user.getActive())) {
			throw new InvalidStateException("Console user " + id + " is inactive");
		}
		return user;
	}

	private ConsoleUser find(UUID id) {
		return users.findById(id).orElseThrow(() -> new NotFoundException("Console user not found: " + id));
	}

	/** First letter of the first two words: "Ana Beltrán" gives "AB". */
	static String initialsOf(String fullName) {
		StringBuilder initials = new StringBuilder();
		for (String word : fullName.trim().split("\\s+")) {
			if (!word.isEmpty() && initials.length() < 2) {
				initials.append(word.substring(0, 1).toUpperCase(Locale.ROOT));
			}
		}
		return initials.toString();
	}

}
