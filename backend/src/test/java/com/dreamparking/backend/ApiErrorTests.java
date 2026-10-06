package com.dreamparking.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

/** Errors are answered as Problem Details with a message the client can show. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class ApiErrorTests {

	@Autowired
	MockMvc mvc;

	@Test
	void businessErrorsCarryTheirMessage() throws Exception {
		String id = startRequest();
		mvc.perform(post("/api/onboarding/requests/{id}/submit", id))
			.andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(409))
			.andExpect(jsonPath("$.title").value("Conflict"))
			.andExpect(jsonPath("$.detail").value("Complete the 4 previous steps before submitting"))
			.andExpect(jsonPath("$.instance").value("/api/onboarding/requests/" + id + "/submit"));

		UUID missing = UUID.randomUUID();
		mvc.perform(get("/api/onboarding/requests/{id}", missing))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Onboarding request not found: " + missing));

		mvc.perform(put("/api/onboarding/requests/{id}/income", id).contentType(MediaType.APPLICATION_JSON).content("""
				{"sourceCode": "OTRO", "rangeCode": "MENOS_500"}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("sourceDetail is required when the income source is OTRO"));
	}

	@Test
	void validationErrorsListEachField() throws Exception {
		String id = startRequest();
		mvc.perform(put("/api/onboarding/requests/{id}/basic-data", id).contentType(MediaType.APPLICATION_JSON).content("""
				{"dui": "123", "firstNames": "Ana", "lastNames": "", "mobilePhone": "2222-0000"}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.dui").exists())
			.andExpect(jsonPath("$.errors.lastNames").exists())
			.andExpect(jsonPath("$.errors.mobilePhone").exists())
			.andExpect(jsonPath("$.errors.firstNames").doesNotExist());
	}

	@Test
	void malformedBodiesAndPathsAreBadRequests() throws Exception {
		String id = startRequest();
		mvc.perform(put("/api/onboarding/requests/{id}/expected-activity", id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"transactionTypeCode\": \"AHORRO\", \"monthlyAmountRangeCode\": "))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail")
				.value("The request body is not valid JSON or a field has a value of the wrong type."));

		mvc.perform(get("/api/onboarding/requests/no-es-un-uuid"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400));
	}

	private String startRequest() throws Exception {
		String body = mvc.perform(post("/api/onboarding/requests")).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

}
