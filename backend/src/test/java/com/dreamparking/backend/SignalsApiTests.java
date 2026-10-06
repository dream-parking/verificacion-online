package com.dreamparking.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.console.ConsoleRole;
import com.jayway.jsonpath.JsonPath;

/** VDI-41 / VDI-42: device and behavior signals are stored with the request and shown in the console. */
@Import({ TestcontainersConfiguration.class, TestAuth.class })
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class SignalsApiTests {

	@Autowired
	WebApplicationContext context;

	@Autowired
	TestAuth auth;

	MockMvc mvc;

	@BeforeEach
	void signIn() {
		mvc = auth.mockMvcAs(context, auth.user(ConsoleRole.ADMIN));
	}

	@Autowired
	EntityManager em;

	@Test
	void storesSignalsAndShowsThemInTheConsole() throws Exception {
		String id = startRequest();

		mvc.perform(put("/api/onboarding/requests/{id}/signals", id).contentType(MediaType.APPLICATION_JSON)
			.header(HttpHeaders.USER_AGENT, "verificacion-app/1.0")
			.content("""
					{"deviceFingerprint": "aa11·bb22·cc33", "deviceModel": "iPhone 15", "operatingSystem": "iOS",
					 "appVersion": "1.0.0+1", "approximateLocation": "San Salvador, El Salvador", "countryIso": "sv",
					 "typingSpeedCpm": 185,
					 "steps": [{"step": "PRIVACY_NOTICE", "startedAt": "2026-10-06T10:00:00Z", "completedAt": "2026-10-06T10:01:08Z"},
					           {"step": "INCOME", "startedAt": "2026-10-06T10:01:08Z", "attempts": 2}]}
					"""))
			.andExpect(status().isNoContent());

		flushAndClear();
		mvc.perform(get("/api/console/requests/{id}", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.signals.ip").value("127.0.0.1"))
			.andExpect(jsonPath("$.signals.deviceFingerprint").value("aa11·bb22·cc33"))
			.andExpect(jsonPath("$.signals.device").value("iPhone 15 · iOS"))
			.andExpect(jsonPath("$.signals.approximateLocation").value("San Salvador, El Salvador"))
			.andExpect(jsonPath("$.signals.typingSpeedCpm").value(185))
			.andExpect(jsonPath("$.signals.typingPace").value("NORMAL"))
			.andExpect(jsonPath("$.signals.requestsFromSameDevice").value(1))
			.andExpect(jsonPath("$.steps.length()").value(2))
			.andExpect(jsonPath("$.steps[0].step").value("PRIVACY_NOTICE"))
			.andExpect(jsonPath("$.steps[0].durationSeconds").value(68))
			.andExpect(jsonPath("$.steps[1].attempts").value(2));
	}

	@Test
	void classifiesTheTypingPace() throws Exception {
		String id = startRequest();
		String[][] cases = { { "95", "SLOW" }, { "150", "NORMAL" }, { "310", "FAST" } };
		for (String[] c : cases) {
			capture(id, "{\"deviceFingerprint\": \"fp-ritmo\", \"typingSpeedCpm\": " + c[0] + "}", 204);
			flushAndClear();
			mvc.perform(get("/api/console/requests/{id}", id))
				.andExpect(jsonPath("$.signals.typingPace").value(c[1]));
		}
	}

	@Test
	void sendingSignalsAgainReplacesThemAndCountsRequestsPerDevice() throws Exception {
		String first = startRequest();
		String second = startRequest();

		capture(first, "{\"deviceFingerprint\": \"fp-compartido\", \"approximateLocation\": \"Santa Ana\"}", 204);
		capture(first, "{\"deviceFingerprint\": \"fp-compartido\", \"approximateLocation\": \"San Miguel\"}", 204);
		capture(second, "{\"deviceFingerprint\": \"fp-compartido\"}", 204);

		flushAndClear();
		mvc.perform(get("/api/console/requests/{id}", first))
			.andExpect(jsonPath("$.signals.approximateLocation").value("San Miguel"))
			.andExpect(jsonPath("$.signals.requestsFromSameDevice").value(2));
	}

	@Test
	void rejectsInvalidSignals() throws Exception {
		String id = startRequest();

		capture(id, "{}", 400);
		capture(id, "{\"deviceFingerprint\": \"  \"}", 400);
		capture(id, "{\"deviceFingerprint\": \"fp\", \"typingSpeedCpm\": 5000}", 400);
		capture(id, "{\"deviceFingerprint\": \"fp\", \"typingSpeedCpm\": -1}", 400);
		capture(id, "{\"deviceFingerprint\": \"fp\", \"countryIso\": \"SLV\"}", 400);
		capture(id, "{\"deviceFingerprint\": \"fp\", \"steps\": [{\"step\": \"NO_EXISTE\", \"startedAt\": \"2026-10-06T10:00:00Z\"}]}",
				400);
		capture(id, "{\"deviceFingerprint\": \"fp\", \"steps\": [{\"step\": \"INCOME\", \"startedAt\": \"2026-10-06T10:00:00Z\", \"completedAt\": \"2026-10-06T09:00:00Z\"}]}",
				400);
	}

	@Test
	void signalsOfUnknownOrSubmittedRequestsAreRejected() throws Exception {
		capture(UUID.randomUUID().toString(), "{\"deviceFingerprint\": \"fp\"}", 404);
		capture(EntityMappingTests.DEMO_REQUEST.toString(), "{\"deviceFingerprint\": \"fp\"}", 409);
	}

	private void capture(String id, String body, int expectedStatus) throws Exception {
		mvc.perform(put("/api/onboarding/requests/{id}/signals", id).contentType(MediaType.APPLICATION_JSON)
			.content(body)).andExpect(status().is(expectedStatus));
	}

	private String startRequest() throws Exception {
		String body = mvc.perform(post("/api/onboarding/requests")).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	/** The console reads views, which Hibernate does not know depend on the tables written above. */
	private void flushAndClear() {
		em.flush();
		em.clear();
	}

}
