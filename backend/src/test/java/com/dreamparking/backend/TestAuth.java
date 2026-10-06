package com.dreamparking.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.dreamparking.backend.console.ConsoleRole;
import com.dreamparking.backend.console.ConsoleUser;
import com.dreamparking.backend.console.ConsoleUserRepository;
import com.dreamparking.backend.security.TokenService;

/** Test helper (import it with {@code @Import}): creates console users with a password and signs tokens for them. */
public class TestAuth {

	public static final String PASSWORD = "una-clave-larga-1";

	private final ConsoleUserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final TokenService tokens;

	public TestAuth(ConsoleUserRepository users, PasswordEncoder passwordEncoder, TokenService tokens) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.tokens = tokens;
	}

	/** A new active user with the given role and {@link #PASSWORD}, saved in the current transaction. */
	public ConsoleUser user(ConsoleRole role) {
		ConsoleUser user = new ConsoleUser();
		user.setEmail("test-" + UUID.randomUUID() + "@ceiba.example");
		user.setFullName("Usuario de Prueba");
		user.setJobTitle("Pruebas");
		user.setInitials("UP");
		user.setRole(role);
		user.setActive(true);
		user.changePasswordHash(passwordEncoder.encode(PASSWORD), java.time.Instant.now());
		return users.saveAndFlush(user);
	}

	public String bearer(ConsoleUser user) {
		return "Bearer " + tokens.issue(user).value();
	}

	/** A MockMvc that sends the user's token on every request. */
	public MockMvc mockMvcAs(WebApplicationContext context, ConsoleUser user) {
		return MockMvcBuilders.webAppContextSetup(context)
			.apply(SecurityMockMvcConfigurers.springSecurity())
			.defaultRequest(get("/").header(HttpHeaders.AUTHORIZATION, bearer(user)))
			.build();
	}

}
