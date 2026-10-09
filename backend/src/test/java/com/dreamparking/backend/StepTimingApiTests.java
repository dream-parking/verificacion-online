package com.dreamparking.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.dreamparking.backend.console.entity.enums.ConsoleRole;
import com.jayway.jsonpath.JsonPath;

/** The app is the only source of step times and attempts; the backend only tracks the progress. */
@Import({ TestcontainersConfiguration.class, TestAuth.class })
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class StepTimingApiTests {

	@Autowired
	WebApplicationContext context;

	@Autowired
	TestAuth auth;

	@Autowired
	EntityManager entityManager;

	MockMvc mvc;

	@BeforeEach
	void signIn() {
		mvc = auth.mockMvcAs(context, auth.user(ConsoleRole.ADMIN));
	}

	@Test
	void savingAStepDoesNotOverwriteTheTimesSentByTheApp() throws Exception {
		String id = startRequest();
		Instant shown = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		signals(id, """
				[{"step": "PRIVACY_NOTICE", "startedAt": "%s", "completedAt": "%s", "attempts": 2}]
				""".formatted(shown, shown.plusSeconds(40))).andExpect(status().isNoContent());

		send(put("/api/onboarding/requests/{id}/privacy-consent", id), "{\"signalsAccepted\": true}")
			.andExpect(jsonPath("$.completedSteps").value(1));

		flushAndClear();
		mvc.perform(get("/api/console/requests/{id}", id))
			.andExpect(jsonPath("$.completedSteps").value(1))
			.andExpect(jsonPath("$.steps.length()").value(1))
			.andExpect(jsonPath("$.steps[0].durationSeconds").value(40))
			.andExpect(jsonPath("$.steps[0].attempts").value(2));
	}

	@Test
	void theBackendTracksProgressWithoutInventingStepTimes() throws Exception {
		String id = startRequest();
		send(put("/api/onboarding/requests/{id}/privacy-consent", id), "{\"signalsAccepted\": true}");
		send(put("/api/onboarding/requests/{id}/basic-data", id), """
				{"dui": "01234567-8", "firstNames": "Ana", "lastNames": "Pérez", "mobilePhone": "7123-4567"}
				""").andExpect(jsonPath("$.completedSteps").value(2));

		flushAndClear();
		mvc.perform(get("/api/console/requests/{id}", id))
			.andExpect(jsonPath("$.completedSteps").value(2))
			.andExpect(jsonPath("$.steps.length()").value(0));
	}

	@Test
	void impossibleTimesFromThePhoneClockAreRejected() throws Exception {
		String id = startRequest();
		Instant now = Instant.now();

		signals(id, "[{\"step\": \"INCOME\", \"startedAt\": \"" + now.minus(1, ChronoUnit.HOURS) + "\"}]")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Step INCOME starts before the request was created"));
		signals(id, "[{\"step\": \"INCOME\", \"startedAt\": \"" + now + "\", \"completedAt\": \""
				+ now.plus(1, ChronoUnit.HOURS) + "\"}]")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Step INCOME has a time in the future"));

		// A phone clock a couple of minutes off is tolerated
		signals(id, "[{\"step\": \"INCOME\", \"startedAt\": \"" + now + "\", \"completedAt\": \""
				+ now.plus(2, ChronoUnit.MINUTES) + "\"}]")
			.andExpect(status().isNoContent());
	}

	private ResultActions signals(String id, String steps) throws Exception {
		return send(put("/api/onboarding/requests/{id}/signals", id), "{\"deviceFingerprint\": \"fp-pasos\", \"steps\": " + steps + "}");
	}

	private String startRequest() throws Exception {
		return JsonPath.read(mvc.perform(post("/api/onboarding/requests")).andReturn().getResponse().getContentAsString(),
				"$.id");
	}

	private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
		return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}

}
