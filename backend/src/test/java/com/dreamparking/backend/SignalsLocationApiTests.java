package com.dreamparking.backend;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.dreamparking.backend.console.entity.enums.ConsoleRole;
import com.dreamparking.backend.onboarding.geo.GeolocationException;
import com.dreamparking.backend.onboarding.geo.IpGeolocation;
import com.dreamparking.backend.onboarding.geo.IpGeolocationClient;
import com.jayway.jsonpath.JsonPath;

/** VDI-67: the server resolves latitude and longitude from the client IP and marks them «unavailable» otherwise. */
@Import({ TestcontainersConfiguration.class, TestAuth.class })
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class SignalsLocationApiTests {

	static final String PUBLIC_IP = "190.87.195.8";

	static final String BODY = "{\"deviceFingerprint\": \"fp-ubicacion\"}";

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
	void signIn() {
		mvc = auth.mockMvcAs(context, auth.user(ConsoleRole.ADMIN));
	}

	static IpGeolocation sansalvador() {
		return new IpGeolocation("success", null, "El Salvador", "SV", "SS", "San Salvador", "San Salvador", "",
				13.6929, -89.2182, "America/El_Salvador", "Telemovil El Salvador", "Tigo", "AS14754 Telgua");
	}

	@Test
	void storesLatitudeLongitudeAndEverythingTheProviderReturned() throws Exception {
		when(geolocation.locate(any())).thenReturn(sansalvador());
		String id = startRequest();

		capture(id, "{\"deviceFingerprint\": \"fp-ubicacion\", \"approximateLocation\": \"lo que diga la app\", \"countryIso\": \"gt\"}",
				PUBLIC_IP, 204);

		flushAndClear();
		mvc.perform(get("/api/console/requests/{id}", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.signals.ip").value(PUBLIC_IP))
			.andExpect(jsonPath("$.signals.locationStatus").value("AVAILABLE"))
			.andExpect(jsonPath("$.signals.latitude").value(13.6929))
			.andExpect(jsonPath("$.signals.longitude").value(-89.2182))
			.andExpect(jsonPath("$.signals.approximateLocation").value("San Salvador, El Salvador"))
			.andExpect(jsonPath("$.signals.ipDetails.country").value("El Salvador"))
			.andExpect(jsonPath("$.signals.ipDetails.countryCode").value("SV"))
			.andExpect(jsonPath("$.signals.ipDetails.region").value("SS"))
			.andExpect(jsonPath("$.signals.ipDetails.regionName").value("San Salvador"))
			.andExpect(jsonPath("$.signals.ipDetails.city").value("San Salvador"))
			.andExpect(jsonPath("$.signals.ipDetails.timezone").value("America/El_Salvador"))
			.andExpect(jsonPath("$.signals.ipDetails.isp").value("Telemovil El Salvador"))
			.andExpect(jsonPath("$.signals.ipDetails.org").value("Tigo"))
			.andExpect(jsonPath("$.signals.ipDetails.asName").value("AS14754 Telgua"))
			.andExpect(jsonPath("$.signals.ipDetails.failure").doesNotExist())
			.andExpect(jsonPath("$.signals.ipDetails.lookedUpAt").exists());
	}

	@Test
	void failedLookupMarksTheLocationUnavailableAndKeepsTheOtherSignals() throws Exception {
		when(geolocation.locate(any())).thenThrow(new GeolocationException("provider error: HttpClientErrorException"));
		String id = startRequest();

		capture(id, "{\"deviceFingerprint\": \"fp-ubicacion\", \"approximateLocation\": \"Santa Ana\", \"typingSpeedCpm\": 150}",
				PUBLIC_IP, 204);

		flushAndClear();
		mvc.perform(get("/api/console/requests/{id}", id))
			.andExpect(jsonPath("$.signals.locationStatus").value("UNAVAILABLE"))
			.andExpect(jsonPath("$.signals.latitude").doesNotExist())
			.andExpect(jsonPath("$.signals.longitude").doesNotExist())
			.andExpect(jsonPath("$.signals.ipDetails.failure").value("provider error: HttpClientErrorException"))
			.andExpect(jsonPath("$.signals.approximateLocation").value("Santa Ana"))
			.andExpect(jsonPath("$.signals.typingPace").value("NORMAL"))
			.andExpect(jsonPath("$.signals.ip").value(PUBLIC_IP));
	}

	@Test
	void privateAddressesAreNotSentToTheProvider() throws Exception {
		String id = startRequest();

		capture(id, BODY, "192.168.1.20", 204);

		verify(geolocation, never()).locate(any());
		flushAndClear();
		mvc.perform(get("/api/console/requests/{id}", id))
			.andExpect(jsonPath("$.signals.locationStatus").value("UNAVAILABLE"))
			.andExpect(jsonPath("$.signals.ipDetails.failure").value("private or reserved address"));
	}

	@Test
	void aLaterFailureDoesNotEraseTheLocationAlreadyFoundForTheSameIp() throws Exception {
		when(geolocation.locate(any())).thenReturn(sansalvador()).thenThrow(new GeolocationException("rate limited"));
		String id = startRequest();

		capture(id, BODY, PUBLIC_IP, 204);
		capture(id, BODY, PUBLIC_IP, 204);

		flushAndClear();
		mvc.perform(get("/api/console/requests/{id}", id))
			.andExpect(jsonPath("$.signals.locationStatus").value("AVAILABLE"))
			.andExpect(jsonPath("$.signals.latitude").value(13.6929));
	}

	@Test
	void aNewIpReplacesThePreviousLocationEvenIfItCannotBeLocated() throws Exception {
		when(geolocation.locate(any())).thenReturn(sansalvador()).thenThrow(new GeolocationException("rate limited"));
		String id = startRequest();

		capture(id, BODY, PUBLIC_IP, 204);
		capture(id, BODY, "8.8.8.8", 204);

		flushAndClear();
		mvc.perform(get("/api/console/requests/{id}", id))
			.andExpect(jsonPath("$.signals.locationStatus").value("UNAVAILABLE"))
			.andExpect(jsonPath("$.signals.latitude").doesNotExist());
	}

	@Test
	void unknownRequestsDoNotTriggerALookup() throws Exception {
		capture(UUID.randomUUID().toString(), BODY, PUBLIC_IP, 404);

		verify(geolocation, never()).locate(any());
	}

	private void capture(String id, String body, String remoteAddress, int expectedStatus) throws Exception {
		RequestPostProcessor from = request -> {
			request.setRemoteAddr(remoteAddress);
			return request;
		};
		mvc.perform(put("/api/onboarding/requests/{id}/signals", id).with(from)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)).andExpect(status().is(expectedStatus));
	}

	private String startRequest() throws Exception {
		String body = mvc.perform(post("/api/onboarding/requests")).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private void flushAndClear() {
		em.flush();
		em.clear();
	}

}
