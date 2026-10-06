package com.dreamparking.backend.console;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first administrator from {@code APP_BOOTSTRAP_ADMIN_EMAIL} and {@code APP_BOOTSTRAP_ADMIN_PASSWORD}.
 * Without it nobody could sign in and create the other users. It only fills gaps: an existing user is never
 * modified, and the password is never written to the log.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

	private final ConsoleUserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final Clock clock;

	private final String email;

	private final String password;

	public AdminBootstrap(ConsoleUserRepository users, PasswordEncoder passwordEncoder, Clock clock,
			@Value("${app.bootstrap-admin.email:}") String email,
			@Value("${app.bootstrap-admin.password:}") String password) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
		this.email = email.trim().toLowerCase();
		this.password = password;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (email.isEmpty() || password.isEmpty()) {
			return;
		}
		PasswordPolicy.check(password, email);
		if (users.existsByEmailIgnoreCase(email)) {
			log.info("Bootstrap administrator {} already exists: leaving it untouched", email);
			return;
		}
		ConsoleUser admin = new ConsoleUser();
		admin.setEmail(email);
		admin.setFullName("Administrador");
		admin.setJobTitle("Administrador de la consola");
		admin.setInitials("AD");
		admin.setRole(ConsoleRole.ADMIN);
		admin.setActive(true);
		admin.changePasswordHash(passwordEncoder.encode(password), clock.instant());
		users.save(admin);
		log.info("Bootstrap administrator {} created", email);
	}

}
