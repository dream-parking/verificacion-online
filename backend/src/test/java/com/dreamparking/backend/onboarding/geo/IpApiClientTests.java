package com.dreamparking.backend.onboarding.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** VDI-67: the ip-api.com client, against a local server that answers like the provider does. */
class IpApiClientTests {

	static final String SUCCESS = """
			{"status":"success","country":"El Salvador","countryCode":"SV","region":"SS","regionName":"San Salvador",
			 "city":"San Salvador","zip":"","lat":13.6929,"lon":-89.2182,"timezone":"America/El_Salvador",
			 "isp":"Telemovil El Salvador","org":"Tigo","as":"AS14754 Telgua","query":"190.87.195.8","extra":1}
			""";

	HttpServer server;

	int statusCode;

	String body;

	long delayMillis;

	final AtomicReference<String> requested = new AtomicReference<>();

	@BeforeEach
	void startProvider() throws Exception {
		statusCode = 200;
		body = SUCCESS;
		delayMillis = 0;
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/json", exchange -> {
			requested.set(exchange.getRequestURI().toString());
			try {
				Thread.sleep(delayMillis);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(statusCode, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
	}

	@AfterEach
	void stopProvider() {
		server.stop(0);
	}

	IpApiClient client(boolean enabled, String query, Duration timeout) {
		return new IpApiClient(enabled, "http://localhost:" + server.getAddress().getPort() + "/json", query, timeout);
	}

	InetAddress ip() throws Exception {
		return InetAddress.getByName("190.87.195.8");
	}

	@Test
	void mapsEveryFieldTheProviderReturns() throws Exception {
		IpGeolocation geo = client(true, "", Duration.ofSeconds(2)).locate(ip());

		assertThat(geo.lat()).isEqualTo(13.6929);
		assertThat(geo.lon()).isEqualTo(-89.2182);
		assertThat(geo.country()).isEqualTo("El Salvador");
		assertThat(geo.countryCode()).isEqualTo("SV");
		assertThat(geo.region()).isEqualTo("SS");
		assertThat(geo.regionName()).isEqualTo("San Salvador");
		assertThat(geo.city()).isEqualTo("San Salvador");
		assertThat(geo.timezone()).isEqualTo("America/El_Salvador");
		assertThat(geo.isp()).isEqualTo("Telemovil El Salvador");
		assertThat(geo.org()).isEqualTo("Tigo");
		assertThat(geo.asName()).isEqualTo("AS14754 Telgua");
		assertThat(requested.get()).startsWith("/json/190.87.195.8?fields=status,message,");
	}

	@Test
	void appendsTheConfiguredQuery() throws Exception {
		client(true, "key=abc", Duration.ofSeconds(2)).locate(ip());

		assertThat(requested.get()).endsWith("&key=abc");
	}

	@Test
	void providerFailureCarriesItsMessage() {
		body = "{\"status\":\"fail\",\"message\":\"reserved range\"}";

		assertThatThrownBy(() -> client(true, "", Duration.ofSeconds(2)).locate(ip()))
			.isInstanceOf(GeolocationException.class)
			.hasMessage("reserved range");
	}

	@Test
	void failWithoutMessageAndSuccessWithoutCoordinatesAreNotLocated() {
		body = "{\"status\":\"fail\"}";
		assertThatThrownBy(() -> client(true, "", Duration.ofSeconds(2)).locate(ip()))
			.hasMessage("address not located");

		body = "{\"status\":\"success\",\"country\":\"El Salvador\"}";
		assertThatThrownBy(() -> client(true, "", Duration.ofSeconds(2)).locate(ip()))
			.hasMessage("address not located");
	}

	@Test
	void rateLimitAndServerErrorsAreFailures() {
		statusCode = 429;
		assertThatThrownBy(() -> client(true, "", Duration.ofSeconds(2)).locate(ip()))
			.isInstanceOf(GeolocationException.class)
			.hasMessageStartingWith("provider error");

		statusCode = 500;
		assertThatThrownBy(() -> client(true, "", Duration.ofSeconds(2)).locate(ip()))
			.isInstanceOf(GeolocationException.class);
	}

	@Test
	void emptyBodyIsAFailure() {
		body = "null";

		assertThatThrownBy(() -> client(true, "", Duration.ofSeconds(2)).locate(ip()))
			.isInstanceOf(GeolocationException.class);
	}

	@Test
	void slowProviderTimesOut() {
		delayMillis = 1500;

		assertThatThrownBy(() -> client(true, "", Duration.ofMillis(300)).locate(ip()))
			.isInstanceOf(GeolocationException.class)
			.hasMessageStartingWith("provider error");
	}

	@Test
	void unreachableProviderIsAFailure() {
		server.stop(0);

		assertThatThrownBy(() -> client(true, "", Duration.ofMillis(500)).locate(ip()))
			.isInstanceOf(GeolocationException.class);
	}

	@Test
	void disabledClientNeverCallsTheProvider() {
		assertThatThrownBy(() -> client(false, "", Duration.ofSeconds(2)).locate(ip()))
			.hasMessage("geolocation is disabled");
		assertThat(requested.get()).isNull();
	}

	@Test
	void anAddressAlreadyLocatedIsAnsweredWithoutAskingTheProviderAgain() throws Exception {
		IpApiClient client = client(true, "", Duration.ofSeconds(2));
		IpGeolocation first = client.locate(ip());
		requested.set(null);
		statusCode = 500;

		assertThat(client.locate(ip())).isEqualTo(first);
		assertThat(requested.get()).isNull();
	}

	@Test
	void failuresAreNotCached() throws Exception {
		IpApiClient client = client(true, "", Duration.ofSeconds(2));
		statusCode = 429;
		assertThatThrownBy(() -> client.locate(ip())).isInstanceOf(GeolocationException.class);
		statusCode = 200;

		assertThat(client.locate(ip()).city()).isEqualTo("San Salvador");
	}

}
