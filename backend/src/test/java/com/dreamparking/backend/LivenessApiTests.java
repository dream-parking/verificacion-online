package com.dreamparking.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Arrays;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.dreamparking.backend.console.entity.enums.ConsoleRole;
import com.dreamparking.backend.liveness.client.LivenessClient;
import com.dreamparking.backend.liveness.client.LivenessException;
import com.dreamparking.backend.liveness.client.LivenessResult;
import com.dreamparking.backend.liveness.client.SelfieProblem;
import com.jayway.jsonpath.JsonPath;

/**
 * VDI-114: the app sends the selfie, the provider gives a confidence, and the file keeps it with the provider, the
 * attempts and the selfie encrypted. The provider is mocked; its client has its own tests.
 */
@Import({ TestcontainersConfiguration.class, TestAuth.class })
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class LivenessApiTests {

	/** Smallest JPEG and PNG headers: the server only checks the first bytes. */
	static final byte[] SELFIE = { (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 1, 2, 3, 4, 5 };

	static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 9, 8, 7 };

	@Autowired
	WebApplicationContext context;

	@Autowired
	TestAuth auth;

	@Autowired
	EntityManager em;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean
	LivenessClient liveness;

	MockMvc mvc;

	@BeforeEach
	void signIn() {
		mvc = auth.mockMvcAs(context, auth.user(ConsoleRole.FRAUD_ANALYST));
		when(liveness.provider()).thenReturn("openai:gpt-6-astra");
	}

	@Test
	void keepsTheConfidenceTheProviderAndTheEncryptedSelfieInTheFile() throws Exception {
		when(liveness.check(any())).thenReturn(new LivenessResult(new BigDecimal("0.91")));
		String id = startRequest();

		upload(id, SELFIE, "image/jpeg").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CHECKED"))
			.andExpect(jsonPath("$.attempts").value(1))
			// the confidence is a fraud signal: it stays in the file
			.andExpect(jsonPath("$.confidence").doesNotExist());

		assertThat(jdbc.queryForObject("select confidence from liveness_check where request_id = ?::uuid",
				BigDecimal.class, id)).isEqualByComparingTo("0.91");
		assertThat(jdbc.queryForObject("select provider from liveness_check where request_id = ?::uuid", String.class,
				id)).isEqualTo("openai:gpt-6-astra");
		assertThat(jdbc.queryForObject("select status from liveness_check where request_id = ?::uuid", String.class,
				id)).isEqualTo("CHECKED");
		byte[] stored = jdbc.queryForObject("select ciphertext from liveness_check where request_id = ?::uuid",
				byte[].class, id);
		assertThat(stored).isNotEqualTo(SELFIE).hasSizeGreaterThan(SELFIE.length);
		assertThat(jdbc.queryForObject("select count(*) from request_event where request_id = ?::uuid "
				+ "and type = 'LIVENESS_CHECKED'", Integer.class, id)).isEqualTo(1);
	}

	@Test
	void everyTryCountsAndTheLastAnswerIsTheOneKept() throws Exception {
		when(liveness.check(any())).thenReturn(new LivenessResult(new BigDecimal("0.20")))
			.thenReturn(new LivenessResult(new BigDecimal("0.88")));
		String id = startRequest();

		upload(id, SELFIE, "image/jpeg").andExpect(jsonPath("$.attempts").value(1));
		upload(id, PNG, "image/png").andExpect(status().isOk()).andExpect(jsonPath("$.attempts").value(2));
		em.flush();

		assertThat(jdbc.queryForObject("select count(*) from liveness_check where request_id = ?::uuid",
				Integer.class, id)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select confidence from liveness_check where request_id = ?::uuid",
				BigDecimal.class, id)).isEqualByComparingTo("0.88");
		assertThat(jdbc.queryForObject("select content_type from liveness_check where request_id = ?::uuid",
				String.class, id)).isEqualTo("image/png");
	}

	@Test
	void aProviderFailureDoesNotBlockTheRequestAndLeavesNoConfidence() throws Exception {
		when(liveness.check(any())).thenThrow(new LivenessException("provider error: HttpServerErrorException"));
		String id = startRequest();

		upload(id, SELFIE, "image/jpeg").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("FAILED"))
			.andExpect(jsonPath("$.attempts").value(1));

		assertThat(jdbc.queryForObject("select confidence from liveness_check where request_id = ?::uuid",
				BigDecimal.class, id)).isNull();
		assertThat(jdbc.queryForObject("select failure from liveness_check where request_id = ?::uuid", String.class,
				id)).contains("provider error");
	}

	@Test
	void theLivenessConfidenceCountsInTheScoreOfTheRequest() throws Exception {
		when(liveness.check(any())).thenReturn(new LivenessResult(new BigDecimal("0.20")));
		String id = startRequest();
		upload(id, SELFIE, "image/jpeg").andExpect(status().isOk());

		perform(put("/api/onboarding/requests/{id}/expected-activity", id)
			.content("{\"transactionTypeCode\": \"PAGO_SALARIO\", \"monthlyAmountRangeCode\": \"HASTA_200\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.modelVersion").value(2))
			.andExpect(jsonPath("$.signals[?(@.signal == 'LIVENESS')].points").value(8))
			.andExpect(jsonPath("$.signals[?(@.signal == 'LIVENESS')].maxPoints").value(10))
			.andExpect(jsonPath("$.signals[?(@.signal == 'LIVENESS')].observation")
				.value("Confianza de la prueba de vida: 20 %"))
			// no signal rejects by itself: the worst liveness only moves the score, and the request goes on
			.andExpect(jsonPath("$.score").value(8))
			.andExpect(jsonPath("$.level").value("LOW"));
	}

	@Test
	void aSelfieThatCannotBeJudgedIsRejectedWithTheReasonAndSavesNothing() throws Exception {
		when(liveness.check(any())).thenReturn(LivenessResult.unusable(SelfieProblem.TOO_DARK));
		String id = startRequest();

		upload(id, SELFIE, "image/jpeg").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.reason").value("TOO_DARK"));

		assertThat(jdbc.queryForObject("select count(*) from liveness_check where request_id = ?::uuid",
				Integer.class, id)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from request_event where request_id = ?::uuid "
				+ "and type = 'LIVENESS_CHECKED'", Integer.class, id)).isZero();
	}

	@Test
	void anUnusableTryDoesNotCountNorReplaceAGoodResult() throws Exception {
		when(liveness.check(any())).thenReturn(new LivenessResult(new BigDecimal("0.91")))
			.thenReturn(LivenessResult.unusable(SelfieProblem.BLURRY))
			.thenReturn(LivenessResult.unusable(SelfieProblem.GLARE))
			.thenReturn(LivenessResult.unusable(SelfieProblem.TOO_DARK))
			.thenReturn(new LivenessResult(new BigDecimal("0.85")));
		String id = startRequest();

		upload(id, SELFIE, "image/jpeg").andExpect(jsonPath("$.attempts").value(1));
		upload(id, SELFIE, "image/jpeg").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.reason").value("BLURRY"));
		upload(id, SELFIE, "image/jpeg").andExpect(status().isUnprocessableContent());
		upload(id, SELFIE, "image/jpeg").andExpect(status().isUnprocessableContent());
		em.flush();
		// the good result is still there and three bad photos did not make it a repeated try
		assertThat(jdbc.queryForObject("select confidence from liveness_check where request_id = ?::uuid",
				BigDecimal.class, id)).isEqualByComparingTo("0.91");
		assertThat(jdbc.queryForObject("select attempts from liveness_check where request_id = ?::uuid", Integer.class,
				id)).isEqualTo(1);

		// the next judged selfie is the second try, not the fifth
		upload(id, SELFIE, "image/jpeg").andExpect(status().isOk()).andExpect(jsonPath("$.attempts").value(2));
	}

	@Test
	void rejectsAnEmptyOrNonImageSelfie() throws Exception {
		String id = startRequest();

		upload(id, new byte[0], "image/jpeg").andExpect(status().isBadRequest());
		upload(id, "not an image".getBytes(), "image/jpeg").andExpect(status().isBadRequest());
		mvc.perform(multipart(HttpMethod.PUT, "/api/onboarding/requests/{id}/liveness", id))
			.andExpect(status().isBadRequest());

		assertThat(jdbc.queryForObject("select count(*) from liveness_check where request_id = ?::uuid",
				Integer.class, id)).isZero();
	}

	@Test
	void theRequestMustExist() throws Exception {
		upload("00000000-0000-0000-0000-000000000000", SELFIE, "image/jpeg").andExpect(status().isNotFound());
	}

	@Test
	void theFileIsImmutableOnceTheRequestIsNoLongerInProgress() throws Exception {
		when(liveness.check(any())).thenReturn(new LivenessResult(new BigDecimal("0.91")));
		String id = startRequest();
		upload(id, SELFIE, "image/jpeg").andExpect(status().isOk());

		em.flush();
		jdbc.update("update onboarding_request set status = 'ABANDONED', number = null, submitted_at = null "
				+ "where id = ?::uuid", id);
		em.clear();

		upload(id, SELFIE, "image/jpeg").andExpect(status().isConflict());
		// Last on purpose: a statement the database rejects aborts the transaction of the test.
		assertThatThrownBy(() -> jdbc.update("update liveness_check set confidence = 1 where request_id = ?::uuid", id))
			.hasMessageContaining("cannot be modified or deleted");
	}

	@Test
	void theDatabaseRejectsAConfidenceOutOfRange() throws Exception {
		when(liveness.check(any())).thenReturn(new LivenessResult(new BigDecimal("0.91")));
		String id = startRequest();
		upload(id, SELFIE, "image/jpeg").andExpect(status().isOk());
		em.flush();

		assertThatThrownBy(() -> jdbc.update("update liveness_check set confidence = 1.5 where request_id = ?::uuid", id))
			.hasMessageContaining("ck_liveness_check_confidence");
	}

	private String startRequest() throws Exception {
		String id = JsonPath.read(perform(post("/api/onboarding/requests")).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.id");
		perform(put("/api/onboarding/requests/{id}/privacy-consent", id).content("{\"signalsAccepted\": true}"))
			.andExpect(jsonPath("$.completedSteps").value(1));
		return id;
	}

	private ResultActions upload(String id, byte[] selfie, String contentType) throws Exception {
		return mvc.perform(multipart(HttpMethod.PUT, "/api/onboarding/requests/{id}/liveness", id)
			.file(new MockMultipartFile("selfie", "selfie", contentType, Arrays.copyOf(selfie, selfie.length))));
	}

	private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.contentType(MediaType.APPLICATION_JSON));
	}

}
