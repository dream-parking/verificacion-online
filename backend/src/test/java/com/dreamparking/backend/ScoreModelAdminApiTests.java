package com.dreamparking.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.dreamparking.backend.console.entity.ConsoleUser;
import com.dreamparking.backend.console.entity.enums.ConsoleRole;
import com.jayway.jsonpath.JsonPath;

/**
 * VDI-120: an administrator changes the score thresholds without a deploy. Each change is a new version of the
 * model that keeps who published it and when; the evaluations already made keep the version they were computed with.
 */
@Import({ TestcontainersConfiguration.class, TestAuth.class })
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ScoreModelAdminApiTests {

	static final String VERSIONS = "/api/console/admin/score-model/versions";

	@Autowired
	WebApplicationContext context;

	@Autowired
	TestAuth auth;

	ConsoleUser admin;

	MockMvc mvc;

	@BeforeEach
	void signIn() {
		admin = auth.user(ConsoleRole.ADMIN);
		mvc = auth.mockMvcAs(context, admin);
	}

	@Test
	void aNewThresholdAppliesToTheNextRequestsAndTheEarlierOnesKeepTheirVersion() throws Exception {
		// 500 to 1,000 scores 6 points in model v3, which is LOW (up to 39)
		String before = startRequest();
		registerRange(before, "500_1000").andExpect(jsonPath("$.modelVersion").value(3))
			.andExpect(jsonPath("$.score").value(6))
			.andExpect(jsonPath("$.level").value("LOW"));

		publish("{\"lowMax\": 4, \"mediumMax\": 20}").andExpect(status().isCreated())
			.andExpect(jsonPath("$.version").value(4))
			.andExpect(jsonPath("$.inForce").value(true))
			.andExpect(jsonPath("$.validTo").isEmpty())
			.andExpect(jsonPath("$.publishedBy").value(admin.getFullName()))
			.andExpect(jsonPath("$.low.to").value(4))
			.andExpect(jsonPath("$.medium.from").value(5))
			.andExpect(jsonPath("$.medium.to").value(20))
			.andExpect(jsonPath("$.high.from").value(21))
			// the weights are the ones of the version in force
			.andExpect(jsonPath("$.weights[?(@.signal == 'AMOUNT')].maxPoints").value(20))
			.andExpect(jsonPath("$.weights[?(@.signal == 'LIVENESS')].maxPoints").value(10))
			// and the city, which v3 took out of the score, does not come back
			.andExpect(jsonPath("$.weights[?(@.signal == 'CITY')]").isEmpty());

		String after = startRequest();
		registerRange(after, "500_1000").andExpect(jsonPath("$.modelVersion").value(4))
			.andExpect(jsonPath("$.score").value(6))
			.andExpect(jsonPath("$.level").value("MEDIUM"));
		mvc.perform(get("/api/onboarding/requests/{id}/risk-assessment", before))
			.andExpect(jsonPath("$.modelVersion").value(3))
			.andExpect(jsonPath("$.level").value("LOW"));
		mvc.perform(get("/api/console/score-model")).andExpect(jsonPath("$.version").value(4));
	}

	@Test
	void theHistoryShowsWhoPublishedEachVersionAndWhenAndWhichOneIsInForce() throws Exception {
		publish("{\"lowMax\": 30, \"mediumMax\": 60}").andExpect(status().isCreated());

		mvc.perform(get(VERSIONS))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(4))
			.andExpect(jsonPath("$[0].version").value(4))
			.andExpect(jsonPath("$[0].inForce").value(true))
			.andExpect(jsonPath("$[0].publishedBy").value(admin.getFullName()))
			.andExpect(jsonPath("$[0].validFrom").exists())
			.andExpect(jsonPath("$[1].version").value(3))
			.andExpect(jsonPath("$[1].inForce").value(false))
			.andExpect(jsonPath("$[1].validTo").exists())
			// the versions published by a migration have no publisher
			.andExpect(jsonPath("$[1].publishedBy").isEmpty())
			.andExpect(jsonPath("$[2].version").value(2))
			.andExpect(jsonPath("$[3].version").value(1));
	}

	@Test
	void everyVersionClosesTheOneBeforeSoOnlyOneIsInForce() throws Exception {
		publish("{\"lowMax\": 30, \"mediumMax\": 60}").andExpect(status().isCreated());
		publish("{\"lowMax\": 35, \"mediumMax\": 65}").andExpect(status().isCreated())
			.andExpect(jsonPath("$.version").value(5));

		String body = mvc.perform(get(VERSIONS)).andReturn().getResponse().getContentAsString();
		List<Integer> inForce = JsonPath.read(body, "$[?(@.inForce == true)].version");
		List<Integer> closed = JsonPath.read(body, "$[?(@.inForce == false)].version");
		assertThat(inForce).containsExactly(5);
		assertThat(closed).containsExactlyInAnyOrder(1, 2, 3, 4);
	}

	@Test
	void anInvalidThresholdIsNotSavedAndExplainsWhyInSpanish() throws Exception {
		publish("{\"lowMax\": 50, \"mediumMax\": 40}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.lowMax").value(containsString("menor que el fin del nivel Medio")));
		publish("{\"lowMax\": -1, \"mediumMax\": 60}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.lowMax").value(containsString("menor que 0")));
		publish("{\"lowMax\": 10, \"mediumMax\": 100}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.mediumMax").value(containsString("menor que 100")));
		publish("{\"lowMax\": 39.5, \"mediumMax\": 69}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.lowMax").value(containsString("número entero")));
		publish("{\"lowMax\": 39, \"mediumMax\": 69}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.lowMax").value(containsString("los mismos de la versión vigente")));
		publish("{\"mediumMax\": 69}").andExpect(status().isBadRequest());
		publish("{\"lowMax\": \"abc\", \"mediumMax\": 69}").andExpect(status().isBadRequest());
		publish("").andExpect(status().isBadRequest());

		// nothing was saved: v3 is still the one in force
		mvc.perform(get(VERSIONS)).andExpect(jsonPath("$.length()").value(3)).andExpect(jsonPath("$[0].version").value(3));
		mvc.perform(get("/api/console/score-model")).andExpect(jsonPath("$.version").value(3));
	}

	@Test
	void onlyAdministratorsCanSeeOrChangeTheThresholds() throws Exception {
		MockMvc analyst = auth.mockMvcAs(context, auth.user(ConsoleRole.FRAUD_ANALYST));

		analyst.perform(get(VERSIONS)).andExpect(status().isForbidden());
		analyst.perform(post(VERSIONS).contentType(MediaType.APPLICATION_JSON).content("{\"lowMax\": 30, \"mediumMax\": 60}"))
			.andExpect(status().isForbidden());
		// the analyst still reads the model in force
		analyst.perform(get("/api/console/score-model")).andExpect(status().isOk());
		mvc.perform(get(VERSIONS)).andExpect(jsonPath("$.length()").value(3));
	}

	private ResultActions publish(String body) throws Exception {
		return mvc.perform(post(VERSIONS).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private String startRequest() throws Exception {
		String id = JsonPath.read(mvc.perform(post("/api/onboarding/requests").contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.id");
		mvc.perform(put("/api/onboarding/requests/{id}/privacy-consent", id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"signalsAccepted\": true}")).andExpect(status().isOk());
		return id;
	}

	private ResultActions registerRange(String id, String rangeCode) throws Exception {
		return mvc.perform(put("/api/onboarding/requests/{id}/expected-activity", id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"transactionTypeCode\": \"PAGO_SALARIO\", \"monthlyAmountRangeCode\": \"" + rangeCode + "\"}"))
			.andExpect(status().isOk());
	}

}
