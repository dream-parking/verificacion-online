package com.dreamparking.backend.console.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.common.exception.InvalidStateException;
import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.console.dto.ConsoleUserResponse;
import com.dreamparking.backend.console.dto.CreateConsoleUserRequest;
import com.dreamparking.backend.console.entity.ConsoleUser;
import com.dreamparking.backend.console.repository.ConsoleUserRepository;

@Service
public class ConsoleUserService {

	private final ConsoleUserRepository users;

	public ConsoleUserService(ConsoleUserRepository users) {
		this.users = users;
	}

	@Transactional(readOnly = true)
	public List<ConsoleUserResponse> list(boolean activeOnly) {
		List<ConsoleUser> found = activeOnly ? users.findByActiveTrueOrderByFullName() : users.findAllByOrderByFullName();
		return found.stream().map(ConsoleUserResponse::of).toList();
	}

	@Transactional(readOnly = true)
	public ConsoleUserResponse get(UUID userId) {
		return ConsoleUserResponse.of(find(userId));
	}

	@Transactional
	public ConsoleUserResponse create(CreateConsoleUserRequest body) {
		String email = body.email().trim().toLowerCase();
		if (users.existsByEmail(email)) {
			throw new InvalidStateException("A console user with email " + email + " already exists");
		}
		ConsoleUser user = new ConsoleUser();
		user.setEmail(email);
		user.setFullName(body.fullName());
		user.setJobTitle(body.jobTitle());
		user.setInitials(body.initials().toUpperCase());
		user.setRole(body.role());
		return ConsoleUserResponse.of(users.save(user));
	}

	/** Users are deactivated, never deleted: their name stays on the alerts and audit entries they touched. */
	@Transactional
	public ConsoleUserResponse deactivate(UUID userId) {
		ConsoleUser user = find(userId);
		user.setActive(false);
		return ConsoleUserResponse.of(user);
	}

	/** Active user acting in the console; inactive users cannot take or work alerts. */
	@Transactional(readOnly = true)
	public ConsoleUser activeUser(UUID userId) {
		ConsoleUser user = find(userId);
		if (!user.getActive()) {
			throw new InvalidStateException("Console user " + userId + " is inactive");
		}
		return user;
	}

	private ConsoleUser find(UUID userId) {
		return users.findById(userId).orElseThrow(() -> new NotFoundException("Console user not found: " + userId));
	}

}
