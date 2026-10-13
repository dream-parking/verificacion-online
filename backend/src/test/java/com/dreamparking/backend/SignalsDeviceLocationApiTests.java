package com.dreamparking.backend;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.dreamparking.backend.console.entity.enums.ConsoleRole;
import com.dreamparking.backend.onboarding.geo.IpGeolocation;
import com.dreamparking.backend.onboarding.geo.IpGeolocationClient;
import com.jayway.jsonpath.JsonPath;

/**
 * VDI-72: the city and country the phone reports are stored with their capture time once the customer accepted the
 * privacy notice; «not available» is told apart from «not reported yet»; and the console and the score get them.
 */
@Import({ TestcontainersConfiguration.class, TestAuth.class })
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class SignalsDeviceLocationApiTests {

	static final String PUBLIC_IP = "190.87.195.8";

	@Autowired
	WebApplicationContext context;

	@Autowired
	TestAuth auth;

	@Autowired
	EntityManager em;

	@MockitoBean
	IpGeolocationClient geolocation;

	MockMvc mvc;

	@BeforeEach
	void signIn() throws Exception {
		mvc = auth.mockMvcAs(context, auth.user(ConsoleRole.ADMIN));
		when(geolocation.locate(any())).thenReturn(ip("SV", "San Salvador"));
	}

	@Test
	void theCityAndCountryAreStoredWithTheirCaptureTimeAfterTheNoticeIsAccepted() throws Exception {
		String id = startRequest();
		acceptNotice(id);

		signals(id, "{\"deviceFingerprint\": \"fp-city\", \"approximateLocation\": \"  San Salvador \", \"countryIso\": \"sv\"}",
				204);

		console(id).andExpect(jsonPath("$.signals.deviceLocation.status").value("AVAILABLE"))
			.andExpect(jsonPath("$.signals.deviceLocation.city").value("San Salvador"))
			.andExpect(jsonPath("$.signals.deviceLocation.countryIso").value("SV"))
			.andExpect(jsonPath("$.signals.deviceLocation.capturedAt").isNotEmpty())
			.andExpect(jsonPath("$.signals.deviceLocation.matchesIp").value(true));
	}

	@Test
	void onlyTheCityAndTheCountryAreKeptNeverCoordinates() throws Exception {
		String id = startRequest();
		acceptNotice(id);
		signals(id, """
				{"deviceFingerprint": "fp-city", "approximateLocation": "San Salvador", "countryIso": "SV",
				 "latitude": 13.69, "longitude": -89.21, "address": "Calle Real 123"}
				""", 204);

		String body = console(id).andReturn().getResponse().getContentAsString();
		org.assertj.core.api.Assertions
			.assertThat(JsonPath.<java.util.Map<String, Object>>read(body, "$.signals.deviceLocation").keySet())
			.containsExactlyInAnyOrder("status", "city", "countryIso", "matchesIp", "capturedAt");
	}

	@Test
	void withoutTheNoticeTheLocationIsRejectedAndNothingIsSaved() throws Exception {
		String id = startRequest();

		signals(id, "{\"deviceFingerprint\": \"fp-city\", \"approximateLocation\": \"San Salvador\", \"countryIso\": \"SV\"}",
				409);
		signals(id, "{\"deviceFingerprint\": \"fp-city\", \"locationStatus\": \"UNAVAILABLE\"}", 409);

		console(id).andExpect(jsonPath("$.signals").isEmpty());
		// the other signals do not need the notice
		signals(id, "{\"deviceFingerprint\": \"fp-city\", \"typingSpeedCpm\": 150}", 204);
		console(id).andExpect(jsonPath("$.signals.typingPace").value("NORMAL"))
			.andExpect(jsonPath("$.signals.deviceLocation").isEmpty());
	}

	@Test
	void aDeniedPermissionIsUnavailableNotMissingAndTheOtherSignalsAreCaptured() throws Exception {
		String id = startRequest();
		acceptNotice(id);
		signals(id, "{\"deviceFingerprint\": \"fp-city\", \"typingSpeedCpm\": 150}", 204);
		console(id).andExpect(jsonPath("$.signals.deviceLocation").isEmpty());

		signals(id, "{\"deviceFingerprint\": \"fp-city\", \"typingSpeedCpm\": 150, \"locationStatus\": \"UNAVAILABLE\"}", 204);

		console(id).andExpect(jsonPath("$.signals.deviceLocation.status").value("UNAVAILABLE"))
			.andExpect(jsonPath("$.signals.deviceLocation.city").isEmpty())
			.andExpect(jsonPath("$.signals.deviceLocation.countryIso").isEmpty())
			.andExpect(jsonPath("$.signals.deviceLocation.matchesIp").isEmpty())
			.andExpect(jsonPath("$.signals.deviceLocation.capturedAt").isNotEmpty())
			.andExpect(jsonPath("$.signals.typingPace").value("NORMAL"))
			.andExpect(jsonPath("$.signals.deviceFingerprint").value("fp-city"));
		scoreFor(id).andExpect(jsonPath("$.signals[?(@.signal == 'CITY')].points[0]").isEmpty())
			.andExpect(jsonPath("$.signals[?(@.signal == 'CITY')].observation").value("Ubicación no disponible"));
	}

	@Test
	void sendingTheSignalsAgainKeepsTheLocationAndASecondReportReplacesIt() throws Exception {
		String id = startRequest();
		acceptNotice(id);
		signals(id, "{\"deviceFingerprint\": \"fp-city\", \"locationStatus\": \"UNAVAILABLE\"}", 204);
		signals(id, "{\"deviceFingerprint\": \"fp-city\", \"approximateLocation\": \"Santa Ana\", \"countryIso\": \"SV\"}", 204);
		signals(id, "{\"deviceFingerprint\": \"fp-city\", \"typingSpeedCpm\": 150}", 204);

		console(id).andExpect(jsonPath("$.signals.deviceLocation.status").value("AVAILABLE"))
			.andExpect(jsonPath("$.signals.deviceLocation.city").value("Santa Ana"));
	}

	@Test
	void theLocationMustBeCompleteAndCoherent() throws Exception {
		String id = startRequest();
		acceptNotice(id);

		signals(id, "{\"deviceFingerprint\": \"fp\", \"approximateLocation\": \"San Salvador\"}", 400);
		signals(id, "{\"deviceFingerprint\": \"fp\", \"countryIso\": \"SV\"}", 400);
		signals(id, "{\"deviceFingerprint\": \"fp\", \"locationStatus\": \"AVAILABLE\"}", 400);
		signals(id, "{\"deviceFingerprint\": \"fp\", \"locationStatus\": \"UNAVAILABLE\", \"countryIso\": \"SV\"}", 400);
		signals(id, "{\"deviceFingerprint\": \"fp\", \"locationStatus\": \"NO_EXISTE\"}", 400);
		signals(id, "{\"deviceFingerprint\": \"fp\", \"approximateLocation\": \"<script>\", \"countryIso\": \"SV\"}", 400);
	}

	@Test
	void theCityComparedWithTheIpMovesTheScore() throws Exception {
		assertCityPoints("San Salvador", "SV", 0);
		assertCityPoints("san  salvadór", "SV", 0);
		assertCityPoints("Santa Ana", "SV", 10);
		assertCityPoints("Guatemala", "GT", 20);
	}

	@Test
	void aLocationTheIpCannotConfirmAddsAQuarterOfTheWeight() throws Exception {
		when(geolocation.locate(any())).thenThrow(new com.dreamparking.backend.onboarding.geo.GeolocationException("down"));
		assertCityPoints("San Salvador", "SV", 5);
	}

	private void assertCityPoints(String city, String country, int points) throws Exception {
		String id = startRequest();
		acceptNotice(id);
		signals(id, "{\"deviceFingerprint\": \"fp-city\", \"approximateLocation\": \"" + city + "\", \"countryIso\": \""
				+ country + "\"}", 204);

		scoreFor(id).andExpect(jsonPath("$.signals[?(@.signal == 'CITY')].points").value(points))
			.andExpect(jsonPath("$.signals[?(@.signal == 'CITY')].maxPoints").value(20));
	}

	/** Declares the amount, which scores the request with what was captured so far. */
	private ResultActions scoreFor(String id) throws Exception {
		em.flush();
		em.clear();
		return mvc.perform(put("/api/onboarding/requests/{id}/expected-activity", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"transactionTypeCode\": \"PAGO_SALARIO\", \"monthlyAmountRangeCode\": \"HASTA_200\"}"))
			.andExpect(status().isOk());
	}

	private ResultActions console(String id) throws Exception {
		em.flush();
		em.clear();
		return mvc.perform(get("/api/console/requests/{id}", id)).andExpect(status().isOk());
	}

	private void signals(String id, String body, int expectedStatus) throws Exception {
		RequestPostProcessor from = request -> {
			request.setRemoteAddr(PUBLIC_IP);
			return request;
		};
		mvc.perform(put("/api/onboarding/requests/{id}/signals", id).with(from)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)).andExpect(status().is(expectedStatus));
	}

	private void acceptNotice(String id) throws Exception {
		mvc.perform(put("/api/onboarding/requests/{id}/privacy-consent", id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"signalsAccepted\": true}")).andExpect(status().isOk());
	}

	private String startRequest() throws Exception {
		String body = mvc.perform(post("/api/onboarding/requests")).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private static IpGeolocation ip(String countryCode, String city) {
		return new IpGeolocation("success", null, "El Salvador", countryCode, "SS", city, "San Salvador", "", 13.6929,
				-89.2182, "America/El_Salvador", "Telemovil El Salvador", "Tigo", "AS14754 Telgua");
	}

}
