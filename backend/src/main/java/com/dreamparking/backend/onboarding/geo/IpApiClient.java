package com.dreamparking.backend.onboarding.geo;

import java.net.InetAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Client of ip-api.com. The free plan is HTTP only, limited to 45 requests per minute per server and meant for
 * non-commercial use; for a paid plan point {@code app.geolocation.base-url} at {@code https://pro.ip-api.com/json}
 * and add the key to {@code app.geolocation.query} (for example {@code key=...}).
 * <p>
 * Addresses already located are answered from memory for an hour: the app sends the signals several times per
 * request (the last one right before submitting, while the customer waits), and asking the provider again for the
 * same IP only adds its latency and spends the per-minute quota. Failures are not cached.
 */
@Component
public class IpApiClient implements IpGeolocationClient {

	static final String FIELDS = "status,message,country,countryCode,region,regionName,city,zip,lat,lon,timezone,isp,org,as";

	static final Duration CACHE_TTL = Duration.ofHours(1);

	static final int CACHE_MAX_ENTRIES = 10_000;

	private final RestClient http;

	private final Map<InetAddress, Cached> cache = new ConcurrentHashMap<>();

	private final boolean enabled;

	private final String query;

	public IpApiClient(@Value("${app.geolocation.enabled:true}") boolean enabled,
			@Value("${app.geolocation.base-url:http://ip-api.com/json}") String baseUrl,
			@Value("${app.geolocation.query:}") String query,
			@Value("${app.geolocation.timeout:2s}") Duration timeout) {
		this.enabled = enabled;
		this.query = query;
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(timeout).build());
		factory.setReadTimeout(timeout);
		this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
	}

	@Override
	public IpGeolocation locate(InetAddress ip) throws GeolocationException {
		if (!enabled) {
			throw new GeolocationException("geolocation is disabled");
		}
		Cached cached = cache.get(ip);
		if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
			return cached.geolocation();
		}
		IpGeolocation result;
		try {
			result = http.get()
				.uri("/{ip}?fields=" + FIELDS + (query.isBlank() ? "" : "&" + query), ip.getHostAddress())
				.retrieve()
				.body(IpGeolocation.class);
		}
		catch (RestClientException ex) {
			// Includes timeouts, connection errors and HTTP 4xx/5xx (429 = rate limit exceeded).
			throw new GeolocationException("provider error: " + ex.getClass().getSimpleName(), ex);
		}
		if (result == null) {
			throw new GeolocationException("provider returned no data");
		}
		if (!result.located()) {
			throw new GeolocationException(result.message() == null ? "address not located" : result.message());
		}
		if (cache.size() >= CACHE_MAX_ENTRIES) {
			cache.clear();
		}
		cache.put(ip, new Cached(result, Instant.now().plus(CACHE_TTL)));
		return result;
	}

	private record Cached(IpGeolocation geolocation, Instant expiresAt) {
	}

}
