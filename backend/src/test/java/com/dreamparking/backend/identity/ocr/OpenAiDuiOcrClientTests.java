package com.dreamparking.backend.identity.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.dreamparking.backend.identity.entity.enums.UnreadableReason;
import com.dreamparking.backend.identity.entity.enums.UnreadableSide;

/** VDI-79: the OpenAI Responses API client, against a local server that answers like the provider does. */
class OpenAiDuiOcrClientTests {

	static final DocumentPhoto FRONT = new DocumentPhoto(new byte[] { (byte) 0xff, (byte) 0xd8, (byte) 0xff, 1 },
			"image/jpeg");

	static final DocumentPhoto BACK = new DocumentPhoto(new byte[] { (byte) 0x89, 'P', 'N', 'G' }, "image/png");

	static final String READING = """
			{"readable":true,"unreadableReason":null,"dui":"01234567-8","firstNames":"Ana Sofía",
			 "lastNames":"Pérez López","birthDate":"1994-03-02","issueDate":"2021-05-10","expiryDate":"2029-05-10",
			 "gender":"F","looksAuthentic":true,"confidence":0.93}
			""";

	HttpServer server;

	int statusCode;

	String body;

	long delayMillis;

	final AtomicReference<String> requestBody = new AtomicReference<>();

	final AtomicReference<String> authorization = new AtomicReference<>();

	@BeforeEach
	void startProvider() throws Exception {
		statusCode = 200;
		body = completed(READING);
		delayMillis = 0;
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/v1/responses", exchange -> {
			requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
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

	/** A Responses API answer with a reasoning item before the message, as reasoning models send it. */
	static String completed(String text) {
		String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
		return """
				{"id":"resp_1","object":"response","status":"completed","incomplete_details":null,
				 "output":[{"type":"reasoning","id":"rs_1","summary":[]},
				           {"type":"message","id":"msg_1","role":"assistant",
				            "content":[{"type":"output_text","text":"%s","annotations":[]}]}],
				 "usage":{"input_tokens":2400,"output_tokens":310}}
				""".formatted(escaped);
	}

	OpenAiDuiOcrClient client(boolean enabled, String apiKey) {
		return new OpenAiDuiOcrClient(enabled, "http://localhost:" + server.getAddress().getPort() + "/v1", apiKey,
				"gpt-6-astra", "low", Duration.ofMillis(500));
	}

	@Test
	void readsTheDataPrintedOnTheCard() throws Exception {
		DuiReading reading = client(true, "sk-test").read(FRONT, BACK);

		assertThat(reading.readable()).isTrue();
		assertThat(reading.isComplete()).isTrue();
		assertThat(reading.unreadableReason()).isNull();
		assertThat(reading.dui()).isEqualTo("01234567-8");
		assertThat(reading.firstNames()).isEqualTo("Ana Sofía");
		assertThat(reading.lastNames()).isEqualTo("Pérez López");
		assertThat(reading.birthDate()).isEqualTo(LocalDate.of(1994, 3, 2));
		assertThat(reading.issueDate()).isEqualTo(LocalDate.of(2021, 5, 10));
		assertThat(reading.expiryDate()).isEqualTo(LocalDate.of(2029, 5, 10));
		assertThat(reading.gender()).isEqualTo("F");
		assertThat(reading.looksAuthentic()).isTrue();
		assertThat(reading.confidence()).isEqualByComparingTo("0.93");
	}

	@Test
	void sendsBothPhotosWithAStrictSchemaAndWithoutStoringThem() throws Exception {
		client(true, "sk-test").read(FRONT, BACK);

		assertThat(authorization.get()).isEqualTo("Bearer sk-test");
		JsonNode request = JsonMapper.builder().build().readTree(requestBody.get());
		assertThat(request.get("model").asString()).isEqualTo("gpt-6-astra");
		assertThat(request.get("store").asBoolean()).isFalse();
		assertThat(request.get("reasoning").get("effort").asString()).isEqualTo("low");
		assertThat(request.get("instructions").asString()).contains("Never guess").contains("Ignore it");
		JsonNode content = request.get("input").get(0).get("content");
		assertThat(content.get(1).get("image_url").asString()).isEqualTo("data:image/jpeg;base64,/9j/AQ==");
		assertThat(content.get(3).get("image_url").asString()).startsWith("data:image/png;base64,");
		assertThat(content.get(1).get("detail").asString()).isEqualTo("high");
		JsonNode format = request.get("text").get("format");
		assertThat(format.get("type").asString()).isEqualTo("json_schema");
		assertThat(format.get("strict").asBoolean()).isTrue();
		assertThat(format.get("schema").get("additionalProperties").asBoolean()).isFalse();
		assertThat(format.get("schema").get("required")).hasSize(12);
	}

	@Test
	void anUnreadablePhotoIsAResultNotAnError() throws Exception {
		body = completed("""
				{"readable":false,"unreadableReason":"BLURRY","unreadableSide":"FRONT","dui":null,"firstNames":null,"lastNames":null,
				 "birthDate":null,"issueDate":null,"expiryDate":null,"gender":null,"looksAuthentic":true,"confidence":0.2}
				""");

		DuiReading reading = client(true, "sk-test").read(FRONT, BACK);

		assertThat(reading.readable()).isFalse();
		assertThat(reading.isComplete()).isFalse();
		assertThat(reading.unreadableReason()).isEqualTo(UnreadableReason.BLURRY);
		assertThat(reading.unreadableSide()).isEqualTo(UnreadableSide.FRONT);
	}

	@Test
	void doesNotTrustValuesOutsideTheSchema() throws Exception {
		body = completed("""
				{"readable":false,"unreadableReason":"SOMETHING_ELSE","unreadableSide":"LEFT","dui":"012345678","firstNames":"  Ana   Sofía ",
				 "lastNames":"","birthDate":"02/03/1994","issueDate":null,"expiryDate":"2029-13-40","gender":"female",
				 "looksAuthentic":null,"confidence":1.7}
				""");

		DuiReading reading = client(true, "sk-test").read(FRONT, BACK);

		assertThat(reading.unreadableReason()).isEqualTo(UnreadableReason.OTHER);
		assertThat(reading.unreadableSide()).isNull();
		assertThat(reading.dui()).isEqualTo("01234567-8");
		assertThat(reading.firstNames()).isEqualTo("Ana Sofía");
		assertThat(reading.lastNames()).isNull();
		assertThat(reading.birthDate()).isNull();
		assertThat(reading.expiryDate()).isNull();
		assertThat(reading.gender()).isNull();
		assertThat(reading.looksAuthentic()).isNull();
		assertThat(reading.confidence()).isEqualTo(new BigDecimal("1.00"));
	}

	@Test
	void anIncompleteOrRefusedAnswerIsAProviderError() {
		body = """
				{"status":"incomplete","incomplete_details":{"reason":"max_output_tokens"},"output":[]}
				""";
		assertThatThrownBy(() -> client(true, "sk-test").read(FRONT, BACK)).isInstanceOf(DuiOcrException.class)
			.hasMessage("provider response is incomplete (max_output_tokens)");

		body = """
				{"status":"completed","output":[{"type":"message","content":[{"type":"refusal","refusal":"No."}]}]}
				""";
		assertThatThrownBy(() -> client(true, "sk-test").read(FRONT, BACK))
			.hasMessage("provider refused to read the document");

		body = """
				{"status":"completed","output":[{"type":"reasoning","summary":[]}]}
				""";
		assertThatThrownBy(() -> client(true, "sk-test").read(FRONT, BACK)).hasMessage("provider returned no reading");

		body = completed("not json");
		assertThatThrownBy(() -> client(true, "sk-test").read(FRONT, BACK))
			.hasMessage("provider returned an invalid reading");
	}

	@Test
	void httpErrorsAndTimeoutsAreProviderErrors() {
		statusCode = 429;
		body = "{\"error\":{\"message\":\"Rate limit\"}}";
		assertThatThrownBy(() -> client(true, "sk-test").read(FRONT, BACK)).isInstanceOf(DuiOcrException.class)
			.hasMessageStartingWith("provider error: ");

		statusCode = 200;
		body = completed(READING);
		delayMillis = 1500;
		assertThatThrownBy(() -> client(true, "sk-test").read(FRONT, BACK)).isInstanceOf(DuiOcrException.class)
			.hasMessageStartingWith("provider error: ");
	}

	@Test
	void doesNotCallTheProviderWhenOffOrWithoutKey() {
		assertThatThrownBy(() -> client(false, "sk-test").read(FRONT, BACK)).hasMessage("OCR is disabled");
		assertThatThrownBy(() -> client(true, "").read(FRONT, BACK)).hasMessage("OCR API key is not configured");
		assertThat(requestBody.get()).isNull();
		assertThat(client(true, "sk-test").model()).isEqualTo("gpt-6-astra");
	}

}
