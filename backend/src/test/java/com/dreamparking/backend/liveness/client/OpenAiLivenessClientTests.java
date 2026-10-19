package com.dreamparking.backend.liveness.client;

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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.dreamparking.backend.identity.ocr.DocumentPhoto;

/** VDI-114: the OpenAI Responses API client for the liveness check, against a local server that answers like the provider. */
class OpenAiLivenessClientTests {

	static final DocumentPhoto SELFIE = new DocumentPhoto(new byte[] { (byte) 0xff, (byte) 0xd8, (byte) 0xff, 1 },
			"image/jpeg");

	HttpServer server;

	int statusCode;

	String body;

	long delayMillis;

	final AtomicReference<String> requestBody = new AtomicReference<>();

	final AtomicReference<String> authorization = new AtomicReference<>();

	@BeforeEach
	void startProvider() throws Exception {
		statusCode = 200;
		body = completed("{\"faceDetected\":true,\"confidence\":0.87}");
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
				 "usage":{"input_tokens":1200,"output_tokens":90}}
				""".formatted(escaped);
	}

	OpenAiLivenessClient client(boolean enabled, String apiKey) {
		return new OpenAiLivenessClient(enabled, "http://localhost:" + server.getAddress().getPort() + "/v1", apiKey,
				"gpt-6-astra", "low", Duration.ofMillis(500));
	}

	@Test
	void returnsTheConfidenceOfTheModel() throws Exception {
		LivenessResult result = client(true, "sk-test").check(SELFIE);

		assertThat(result.confidence()).isEqualByComparingTo("0.87");
	}

	@Test
	void sendsTheSelfieWithAStrictSchemaAndWithoutStoringIt() throws Exception {
		client(true, "sk-test").check(SELFIE);

		assertThat(authorization.get()).isEqualTo("Bearer sk-test");
		JsonNode request = JsonMapper.builder().build().readTree(requestBody.get());
		assertThat(request.get("model").asString()).isEqualTo("gpt-6-astra");
		assertThat(request.get("store").asBoolean()).isFalse();
		assertThat(request.get("max_output_tokens").asInt()).isEqualTo(OpenAiLivenessClient.MAX_OUTPUT_TOKENS);
		JsonNode content = request.get("input").get(0).get("content");
		assertThat(content).hasSize(2);
		assertThat(content.get(1).get("type").asString()).isEqualTo("input_image");
		assertThat(content.get(1).get("image_url").asString()).startsWith("data:image/jpeg;base64,");
		JsonNode format = request.get("text").get("format");
		assertThat(format.get("type").asString()).isEqualTo("json_schema");
		assertThat(format.get("strict").asBoolean()).isTrue();
		assertThat(format.get("schema").get("additionalProperties").asBoolean()).isFalse();
		assertThat(request.get("instructions").asString()).contains("Ignore it");
	}

	@Test
	void noFaceMeansZeroConfidenceWhateverTheModelSays() throws Exception {
		body = completed("{\"faceDetected\":false,\"confidence\":0.95}");

		assertThat(client(true, "sk-test").check(SELFIE).confidence()).isEqualByComparingTo("0.00");
	}

	@Test
	void keepsTheConfidenceBetweenZeroAndOne() throws Exception {
		body = completed("{\"faceDetected\":true,\"confidence\":1.7}");

		assertThat(client(true, "sk-test").check(SELFIE).confidence()).isEqualByComparingTo("1.00");
	}

	@Test
	void failsWhenItIsDisabledOrHasNoKey() {
		assertThatThrownBy(() -> client(false, "sk-test").check(SELFIE)).isInstanceOf(LivenessException.class)
			.hasMessageContaining("disabled");
		assertThatThrownBy(() -> client(true, "").check(SELFIE)).isInstanceOf(LivenessException.class)
			.hasMessageContaining("API key");
		assertThat(requestBody.get()).isNull();
	}

	@Test
	void failsWhenTheProviderAnswersAnError() {
		statusCode = 429;
		body = "{\"error\":{\"message\":\"rate limit\"}}";

		assertThatThrownBy(() -> client(true, "sk-test").check(SELFIE)).isInstanceOf(LivenessException.class)
			.hasMessageContaining("provider error");
	}

	@Test
	void failsWhenTheProviderTimesOut() {
		delayMillis = 1500;

		assertThatThrownBy(() -> client(true, "sk-test").check(SELFIE)).isInstanceOf(LivenessException.class)
			.hasMessageContaining("provider error");
	}

	@Test
	void failsWhenTheAnswerIsIncompleteRefusedOrInvalid() {
		body = "{\"status\":\"incomplete\",\"incomplete_details\":{\"reason\":\"max_output_tokens\"},\"output\":[]}";
		assertThatThrownBy(() -> client(true, "sk-test").check(SELFIE)).isInstanceOf(LivenessException.class)
			.hasMessageContaining("incomplete (max_output_tokens)");

		body = "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"refusal\"}]}]}";
		assertThatThrownBy(() -> client(true, "sk-test").check(SELFIE)).isInstanceOf(LivenessException.class)
			.hasMessageContaining("refused");

		body = completed("not json");
		assertThatThrownBy(() -> client(true, "sk-test").check(SELFIE)).isInstanceOf(LivenessException.class)
			.hasMessageContaining("invalid answer");

		body = completed("{\"faceDetected\":true}");
		assertThatThrownBy(() -> client(true, "sk-test").check(SELFIE)).isInstanceOf(LivenessException.class)
			.hasMessageContaining("invalid answer");
	}

	@Test
	void namesTheModelAsProvider() {
		assertThat(client(true, "sk-test").provider()).isEqualTo("openai:gpt-6-astra");
	}

}
