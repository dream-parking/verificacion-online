package com.dreamparking.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.dreamparking.backend.common.InvalidInputException;
import com.dreamparking.backend.console.AdminBootstrap;
import com.dreamparking.backend.console.ConsoleRole;
import com.dreamparking.backend.console.ConsoleUser;
import com.dreamparking.backend.console.ConsoleUserRepository;
import com.dreamparking.backend.security.TokenService;
import com.jayway.jsonpath.JsonPath;

import org.springframework.security.crypto.password.PasswordEncoder;

/** Sign-in with email and password, token checks, roles and user management of the console. */
@Import({ TestcontainersConfiguration.class, TestAuth.class })
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class SecurityApiTests {

	private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

	@Autowired
	MockMvc mvc;

	@Autowired
	WebApplicationContext context;

	@Autowired
	TestAuth auth;

	@Autowired
	ConsoleUserRepository users;

	@Autowired
	JwtEncoder jwtEncoder;

	@Autowired
	PasswordEncoder passwordEncoder;

	// ---- What is public and what is not

	@Test
	void mobileFlowAndCatalogsAreOpen() throws Exception {
		mvc.perform(get("/api/catalogs")).andExpect(status().isOk());
		mvc.perform(post("/api/onboarding/requests")).andExpect(status().isCreated());
		mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
		mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
	}

	@Test
	void consoleNeedsAToken() throws Exception {
		mvc.perform(get("/api/console/requests"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.title").value("Unauthorized"));
		mvc.perform(get("/api/console/alerts")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/console/users")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/console/score-rules")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/console/alerts/{id}/take", UUID.randomUUID())).andExpect(status().isUnauthorized());
	}

	@Test
	void unknownPathsAreDenied() throws Exception {
		mvc.perform(get("/api/otra-cosa")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/otra-cosa").header(HttpHeaders.AUTHORIZATION, auth.bearer(auth.user(ConsoleRole.ADMIN))))
			.andExpect(status().isForbidden());
	}

	@Test
	void badTokensAreRejected() throws Exception {
		mvc.perform(get("/api/console/requests").header(HttpHeaders.AUTHORIZATION, "Bearer no-es-un-token"))
			.andExpect(status().isUnauthorized());

		ConsoleUser user = auth.user(ConsoleRole.ADMIN);
		TokenService longGone = new TokenService(jwtEncoder, Clock.fixed(Instant.now().minus(Duration.ofDays(2)), ZoneOffset.UTC),
				Duration.ofHours(8));
		mvc.perform(get("/api/console/requests").header(HttpHeaders.AUTHORIZATION, "Bearer " + longGone.issue(user).value()))
			.andExpect(status().isUnauthorized());

		ConsoleUser stranger = new ConsoleUser();
		stranger.setEmail("fantasma@ceiba.example");
		stranger.setFullName("Fantasma");
		stranger.setJobTitle("N/A");
		stranger.setInitials("F");
		stranger.setRole(ConsoleRole.ADMIN);
		setId(stranger, UUID.randomUUID());
		mvc.perform(get("/api/console/requests").header(HttpHeaders.AUTHORIZATION,
				"Bearer " + new TokenService(jwtEncoder, Clock.systemUTC(), Duration.ofHours(1)).issue(stranger).value()))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void aDeactivatedUserLosesAccessWithTheTokenHeAlreadyHas() throws Exception {
		ConsoleUser analyst = auth.user(ConsoleRole.FRAUD_ANALYST);
		String bearer = auth.bearer(analyst);
		mvc.perform(get("/api/console/auth/me").header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isOk());

		analyst.setActive(false);
		users.saveAndFlush(analyst);

		mvc.perform(get("/api/console/auth/me").header(HttpHeaders.AUTHORIZATION, bearer))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void corsAllowsTheConsoleOriginOnly() throws Exception {
		mvc.perform(options("/api/console/auth/login").header(HttpHeaders.ORIGIN, "http://localhost:3000")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));

		mvc.perform(options("/api/console/auth/login").header(HttpHeaders.ORIGIN, "https://sitio-malicioso.example")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
			.andExpect(status().isForbidden());
	}

	// ---- Sign-in

	@Test
	void signsInAndUsesTheToken() throws Exception {
		ConsoleUser user = auth.user(ConsoleRole.FRAUD_ANALYST);

		String body = login(user.getEmail().toUpperCase(), TestAuth.PASSWORD).andExpect(status().isOk())
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.expiresIn").value(28800))
			.andExpect(jsonPath("$.user.role").value("FRAUD_ANALYST"))
			.andExpect(jsonPath("$.user.email").value(user.getEmail()))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String token = JsonPath.read(body, "$.accessToken");

		mvc.perform(get("/api/console/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(user.getId().toString()));
		mvc.perform(get("/api/console/requests").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk());
	}

	@Test
	void everyKindOfBadSignInGetsTheSameAnswer() throws Exception {
		ConsoleUser active = auth.user(ConsoleRole.ADMIN);
		ConsoleUser inactive = auth.user(ConsoleRole.ADMIN);
		inactive.setActive(false);
		users.saveAndFlush(inactive);

		String detail = JsonPath.read(login(active.getEmail(), "otra-clave-larga-9").andExpect(status().isUnauthorized())
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.detail");
		login("nadie@ceiba.example", TestAuth.PASSWORD).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value(detail));
		login(inactive.getEmail(), TestAuth.PASSWORD).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value(detail));
		// demo users from V4 have no password until an administrator sets one
		login("grosales@ceiba.example", TestAuth.PASSWORD).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value(detail));
	}

	@Test
	void rejectsMalformedSignIn() throws Exception {
		mvc.perform(post("/api/console/auth/login").contentType(JSON).content("{}")).andExpect(status().isBadRequest());
		mvc.perform(post("/api/console/auth/login").contentType(JSON).content("{\"email\": \" \", \"password\": \"x\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void blocksAnEmailAfterFiveFailuresInARow() throws Exception {
		ConsoleUser user = auth.user(ConsoleRole.ADMIN);
		for (int i = 0; i < 5; i++) {
			login(user.getEmail(), "clave-equivocada-" + i).andExpect(status().isUnauthorized());
		}
		login(user.getEmail(), TestAuth.PASSWORD).andExpect(status().isTooManyRequests())
			.andExpect(header().exists(HttpHeaders.RETRY_AFTER))
			.andExpect(jsonPath("$.status").value(429));
	}

	@Test
	void aSuccessfulSignInClearsTheFailures() throws Exception {
		ConsoleUser user = auth.user(ConsoleRole.ADMIN);
		for (int i = 0; i < 4; i++) {
			login(user.getEmail(), "clave-equivocada-" + i).andExpect(status().isUnauthorized());
		}
		login(user.getEmail(), TestAuth.PASSWORD).andExpect(status().isOk());
		for (int i = 0; i < 4; i++) {
			login(user.getEmail(), "clave-equivocada-" + i).andExpect(status().isUnauthorized());
		}
		login(user.getEmail(), TestAuth.PASSWORD).andExpect(status().isOk());
	}

	// ---- Roles

	@Test
	void onlyAdministratorsManageUsers() throws Exception {
		String body = newUserJson("lider-nuevo@ceiba.example", TestAuth.PASSWORD);
		for (ConsoleRole role : new ConsoleRole[] { ConsoleRole.KYC_LEAD, ConsoleRole.FRAUD_ANALYST }) {
			MockMvc as = auth.mockMvcAs(context, auth.user(role));
			as.perform(post("/api/console/users").contentType(JSON).content(body)).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));
			as.perform(put("/api/console/users/{id}", UUID.randomUUID()).contentType(JSON).content(updateJson("Nombre", ConsoleRole.ADMIN, true)))
				.andExpect(status().isForbidden());
			as.perform(post("/api/console/users/{id}/password", UUID.randomUUID()).contentType(JSON)
				.content("{\"newPassword\": \"otra-clave-larga-1\"}")).andExpect(status().isForbidden());
			as.perform(get("/api/console/users").param("includeInactive", "true")).andExpect(status().isForbidden());
			as.perform(get("/api/console/users")).andExpect(status().isOk());
		}
	}

	@Test
	void onlyAnalystsAndAdministratorsTakeAlerts() throws Exception {
		String alertId = JsonPath.read(mvc.perform(get("/api/console/alerts").header(HttpHeaders.AUTHORIZATION,
				auth.bearer(auth.user(ConsoleRole.ADMIN))).param("account", "1156"))
			.andReturn()
			.getResponse()
			.getContentAsString(), "$[0].id");

		auth.mockMvcAs(context, auth.user(ConsoleRole.KYC_LEAD)).perform(post("/api/console/alerts/{id}/take", alertId))
			.andExpect(status().isForbidden());
		auth.mockMvcAs(context, auth.user(ConsoleRole.FRAUD_ANALYST)).perform(post("/api/console/alerts/{id}/take", alertId))
			.andExpect(status().isOk());
	}

	// ---- User management

	@Test
	void anAdministratorCreatesAUserWhoCanThenSignIn() throws Exception {
		MockMvc admin = auth.mockMvcAs(context, auth.user(ConsoleRole.ADMIN));

		admin.perform(post("/api/console/users").contentType(JSON).content(newUserJson("Nueva.Persona@Ceiba.example", "clave-inicial-123")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value("nueva.persona@ceiba.example"))
			.andExpect(jsonPath("$.initials").value("NP"))
			.andExpect(jsonPath("$.role").value("FRAUD_ANALYST"))
			.andExpect(jsonPath("$.active").value(true));

		login("nueva.persona@ceiba.example", "clave-inicial-123").andExpect(status().isOk());
	}

	@Test
	void rejectsInvalidNewUsers() throws Exception {
		MockMvc admin = auth.mockMvcAs(context, auth.user(ConsoleRole.ADMIN));
		ConsoleUser existing = auth.user(ConsoleRole.ADMIN);

		admin.perform(post("/api/console/users").contentType(JSON).content(newUserJson(existing.getEmail(), TestAuth.PASSWORD)))
			.andExpect(status().isConflict());
		admin.perform(post("/api/console/users").contentType(JSON).content(newUserJson("otro@ceiba.example", "corta")))
			.andExpect(status().isBadRequest());
		admin.perform(post("/api/console/users").contentType(JSON).content(newUserJson("otro@ceiba.example", "a".repeat(80))))
			.andExpect(status().isBadRequest());
		admin.perform(post("/api/console/users").contentType(JSON).content(newUserJson("otro@ceiba.example", "otro@ceiba.example")))
			.andExpect(status().isBadRequest());
		admin.perform(post("/api/console/users").contentType(JSON).content(newUserJson("no-es-un-correo", TestAuth.PASSWORD)))
			.andExpect(status().isBadRequest());
		admin.perform(post("/api/console/users").contentType(JSON).content("{\"email\": \"x@ceiba.example\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void deactivatingAUserListsAndBlocksThem() throws Exception {
		MockMvc admin = auth.mockMvcAs(context, auth.user(ConsoleRole.ADMIN));
		ConsoleUser analyst = auth.user(ConsoleRole.FRAUD_ANALYST);

		admin.perform(put("/api/console/users/{id}", analyst.getId()).contentType(JSON)
			.content(updateJson("  Ana  Beltrán ", ConsoleRole.FRAUD_ANALYST, false)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(false))
			.andExpect(jsonPath("$.fullName").value("Ana  Beltrán"))
			.andExpect(jsonPath("$.initials").value("AB"));

		login(analyst.getEmail(), TestAuth.PASSWORD).andExpect(status().isUnauthorized());
		admin.perform(get("/api/console/users").param("includeInactive", "true"))
			.andExpect(jsonPath("$[?(@.id=='" + analyst.getId() + "')].active").value(false));
		admin.perform(get("/api/console/users"))
			.andExpect(jsonPath("$[?(@.id=='" + analyst.getId() + "')]").isEmpty());
		admin.perform(put("/api/console/users/{id}", UUID.randomUUID()).contentType(JSON)
			.content(updateJson("Nadie", ConsoleRole.ADMIN, true))).andExpect(status().isNotFound());
	}

	@Test
	void theLastActiveAdministratorCannotBeRemoved() throws Exception {
		ConsoleUser onlyAdmin = auth.user(ConsoleRole.ADMIN);
		MockMvc as = auth.mockMvcAs(context, onlyAdmin);

		as.perform(put("/api/console/users/{id}", onlyAdmin.getId()).contentType(JSON)
			.content(updateJson("Usuario de Prueba", ConsoleRole.KYC_LEAD, true))).andExpect(status().isConflict());
		as.perform(put("/api/console/users/{id}", onlyAdmin.getId()).contentType(JSON)
			.content(updateJson("Usuario de Prueba", ConsoleRole.ADMIN, false))).andExpect(status().isConflict());

		ConsoleUser second = auth.user(ConsoleRole.ADMIN);
		as.perform(put("/api/console/users/{id}", second.getId()).contentType(JSON)
			.content(updateJson("Usuario de Prueba", ConsoleRole.KYC_LEAD, true))).andExpect(status().isOk());
	}

	@Test
	void anAdministratorResetsAnotherUsersPassword() throws Exception {
		MockMvc admin = auth.mockMvcAs(context, auth.user(ConsoleRole.ADMIN));
		ConsoleUser analyst = auth.user(ConsoleRole.FRAUD_ANALYST);

		admin.perform(post("/api/console/users/{id}/password", analyst.getId()).contentType(JSON)
			.content("{\"newPassword\": \"clave-restablecida-7\"}")).andExpect(status().isNoContent());
		login(analyst.getEmail(), TestAuth.PASSWORD).andExpect(status().isUnauthorized());
		login(analyst.getEmail(), "clave-restablecida-7").andExpect(status().isOk());

		admin.perform(post("/api/console/users/{id}/password", analyst.getId()).contentType(JSON)
			.content("{\"newPassword\": \"corta\"}")).andExpect(status().isBadRequest());
		admin.perform(post("/api/console/users/{id}/password", UUID.randomUUID()).contentType(JSON)
			.content("{\"newPassword\": \"clave-restablecida-7\"}")).andExpect(status().isNotFound());
	}

	@Test
	void aDemoUserGetsAPasswordFromAnAdministratorAndCanSignIn() throws Exception {
		MockMvc admin = auth.mockMvcAs(context, auth.user(ConsoleRole.ADMIN));
		String lider = users.findByEmail("grosales@ceiba.example").orElseThrow().getId().toString();

		admin.perform(post("/api/console/users/{id}/password", lider).contentType(JSON)
			.content("{\"newPassword\": \"clave-de-gerardo-1\"}")).andExpect(status().isNoContent());

		login("grosales@ceiba.example", "clave-de-gerardo-1").andExpect(status().isOk())
			.andExpect(jsonPath("$.user.role").value("KYC_LEAD"));
	}

	@Test
	void usersChangeTheirOwnPassword() throws Exception {
		ConsoleUser user = auth.user(ConsoleRole.KYC_LEAD);
		MockMvc as = auth.mockMvcAs(context, user);

		as.perform(post("/api/console/auth/change-password").contentType(JSON)
			.content(passwordJson("no-es-la-actual-1", "nueva-clave-larga-1"))).andExpect(status().isBadRequest());
		as.perform(post("/api/console/auth/change-password").contentType(JSON)
			.content(passwordJson(TestAuth.PASSWORD, TestAuth.PASSWORD))).andExpect(status().isBadRequest());
		as.perform(post("/api/console/auth/change-password").contentType(JSON)
			.content(passwordJson(TestAuth.PASSWORD, "corta"))).andExpect(status().isBadRequest());
		as.perform(post("/api/console/auth/change-password").contentType(JSON)
			.content(passwordJson(TestAuth.PASSWORD, "nueva-clave-larga-1"))).andExpect(status().isNoContent());

		login(user.getEmail(), TestAuth.PASSWORD).andExpect(status().isUnauthorized());
		login(user.getEmail(), "nueva-clave-larga-1").andExpect(status().isOk());
	}

	@Test
	void listsOnlyActiveUsersWithTheirRole() throws Exception {
		mvc.perform(get("/api/console/users").header(HttpHeaders.AUTHORIZATION, auth.bearer(auth.user(ConsoleRole.KYC_LEAD))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(5)));
	}

	// ---- First administrator

	@Test
	void bootstrapCreatesTheFirstAdministratorOnceAndNeverTouchesExistingUsers() throws Exception {
		AdminBootstrap bootstrap = new AdminBootstrap(users, passwordEncoder, Clock.systemUTC(), " Jefe@Ceiba.example ",
				"clave-del-jefe-123");

		bootstrap.run(null);
		ConsoleUser created = users.findByEmail("jefe@ceiba.example").orElseThrow();
		assertThat(created.getRole()).isEqualTo(ConsoleRole.ADMIN);
		assertThat(created.getActive()).isTrue();
		assertThat(passwordEncoder.matches("clave-del-jefe-123", created.getPasswordHash())).isTrue();
		login("jefe@ceiba.example", "clave-del-jefe-123").andExpect(status().isOk());

		String hash = created.getPasswordHash();
		new AdminBootstrap(users, passwordEncoder, Clock.systemUTC(), "jefe@ceiba.example", "otra-clave-larga-99").run(null);
		assertThat(users.findByEmail("jefe@ceiba.example").orElseThrow().getPasswordHash()).isEqualTo(hash);
		assertThat(users.countByRoleAndActiveTrue(ConsoleRole.ADMIN)).isEqualTo(1);
	}

	@Test
	void bootstrapDoesNothingWithoutConfigurationAndRejectsWeakPasswords() {
		long before = users.count();
		new AdminBootstrap(users, passwordEncoder, Clock.systemUTC(), "", "").run(null);
		new AdminBootstrap(users, passwordEncoder, Clock.systemUTC(), "jefe@ceiba.example", "").run(null);
		assertThat(users.count()).isEqualTo(before);

		assertThatThrownBy(() -> new AdminBootstrap(users, passwordEncoder, Clock.systemUTC(), "jefe@ceiba.example", "corta")
			.run(null)).isInstanceOf(InvalidInputException.class);
	}

	// ---- helpers

	private ResultActions login(String email, String password) throws Exception {
		return mvc.perform(post("/api/console/auth/login").contentType(JSON)
			.content("{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}"));
	}

	private static String newUserJson(String email, String password) {
		return "{\"email\": \"" + email + "\", \"fullName\": \"Nueva Persona\", \"jobTitle\": \"Analista\","
				+ " \"role\": \"FRAUD_ANALYST\", \"initialPassword\": \"" + password + "\"}";
	}

	private static String updateJson(String fullName, ConsoleRole role, boolean active) {
		return "{\"fullName\": \"" + fullName + "\", \"jobTitle\": \"Analista\", \"role\": \"" + role.name()
				+ "\", \"active\": " + active + "}";
	}

	private static String passwordJson(String current, String next) {
		return "{\"currentPassword\": \"" + current + "\", \"newPassword\": \"" + next + "\"}";
	}

	private static void setId(ConsoleUser user, UUID id) throws Exception {
		var field = ConsoleUser.class.getDeclaredField("id");
		field.setAccessible(true);
		field.set(user, id);
	}

}
