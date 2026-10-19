package com.dreamparking.backend.common.openai;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * What the OpenAI vision clients share (DUI reading, VDI-79, and liveness check, VDI-114): the HTTP client, the call
 * to the Responses API ({@code POST /responses}) and the way its answer is read. Each client keeps its own prompt,
 * schema and result.
 */
public final class OpenAiResponses {

	private OpenAiResponses() {
	}

	/** The provider is off, fails, times out, refuses or answers something that cannot be used. */
	public static class OpenAiResponsesException extends Exception {

		public OpenAiResponsesException(String message) {
			super(message);
		}

		public OpenAiResponsesException(String message, Throwable cause) {
			super(message, cause);
		}

	}

	public static RestClient restClient(String baseUrl, Duration timeout) {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
		factory.setReadTimeout(timeout);
		return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
	}

	/**
	 * The body of a request: one user message with the given content, answered as a strict JSON schema, and
	 * {@code store: false} so the provider does not retain the images.
	 */
	public static Map<String, Object> body(String model, String instructions, String reasoningEffort, int maxOutputTokens,
			List<Map<String, Object>> content, String schemaName, Map<String, Object> schema) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", model);
		body.put("store", false);
		body.put("instructions", instructions);
		body.put("input", List.of(Map.of("role", "user", "content", content)));
		body.put("reasoning", Map.of("effort", reasoningEffort));
		body.put("max_output_tokens", maxOutputTokens);
		body.put("text", Map.of("format", Map.of("type", "json_schema", "name", schemaName, "strict", true, "schema", schema)));
		return body;
	}

	/**
	 * Sends the request and returns the JSON text of the answer.
	 * @param refusal message when the model refuses to answer
	 * @param empty message when the answer has no text
	 */
	public static String textOf(RestClient http, String apiKey, Map<String, Object> body, String refusal, String empty)
			throws OpenAiResponsesException {
		ResponsesApiResponse response;
		try {
			response = http.post()
				.uri("/responses")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(ResponsesApiResponse.class);
		}
		catch (RestClientException ex) {
			// Includes timeouts, connection errors and HTTP 4xx/5xx (401 = wrong key, 429 = rate or budget limit).
			throw new OpenAiResponsesException("provider error: " + ex.getClass().getSimpleName(), ex);
		}
		return outputText(response, refusal, empty);
	}

	/** The JSON text of the answer; a refusal or an incomplete answer is a provider error. */
	static String outputText(ResponsesApiResponse response, String refusal, String empty)
			throws OpenAiResponsesException {
		if (response == null) {
			throw new OpenAiResponsesException("provider returned no data");
		}
		if (!"completed".equals(response.status())) {
			String reason = response.incompleteDetails() == null ? "" : " (" + response.incompleteDetails().reason() + ")";
			throw new OpenAiResponsesException("provider response is " + response.status() + reason);
		}
		for (OutputItem item : response.output() == null ? List.<OutputItem>of() : response.output()) {
			for (OutputContent part : item.content() == null ? List.<OutputContent>of() : item.content()) {
				if ("refusal".equals(part.type())) {
					throw new OpenAiResponsesException(refusal);
				}
				if ("output_text".equals(part.type()) && part.text() != null) {
					return part.text();
				}
			}
		}
		throw new OpenAiResponsesException(empty);
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ResponsesApiResponse(String status, List<OutputItem> output,
			@JsonProperty("incomplete_details") IncompleteDetails incompleteDetails) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record IncompleteDetails(String reason) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record OutputItem(String type, List<OutputContent> content) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record OutputContent(String type, String text) {
	}

}
