package com.dreamparking.backend.console;

import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.common.InvalidCredentialsException;
import com.dreamparking.backend.common.TooManyAttemptsException;
import com.dreamparking.backend.security.LoginAttempts;
import com.dreamparking.backend.security.TokenService;

/** Sign-in of console users with email and password. */
@Service
public class AuthService {

	private final ConsoleUserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final TokenService tokens;

	private final LoginAttempts attempts;

	/** Checked when the email is unknown, so a miss takes as long as a wrong password. */
	private final String decoyHash;

	public AuthService(ConsoleUserRepository users, PasswordEncoder passwordEncoder, TokenService tokens,
			LoginAttempts attempts) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.tokens = tokens;
		this.attempts = attempts;
		this.decoyHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	@Transactional(readOnly = true)
	public LoginResponse login(LoginRequest request) {
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		long retryAfter = attempts.retryAfterSeconds(email);
		if (retryAfter > 0) {
			throw new TooManyAttemptsException(retryAfter);
		}

		ConsoleUser user = users.findByEmailIgnoreCase(email).orElse(null);
		String hash = user != null && user.getPasswordHash() != null ? user.getPasswordHash() : decoyHash;
		boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
		if (user == null || user.getPasswordHash() == null || !passwordMatches
				|| !Boolean.TRUE.equals(user.getActive())) {
			attempts.failed(email);
			throw new InvalidCredentialsException();
		}

		attempts.succeeded(email);
		TokenService.IssuedToken token = tokens.issue(user);
		return new LoginResponse(token.value(), "Bearer", token.expiresInSeconds(), ConsoleUserResponse.of(user));
	}

}
