package com.dreamparking.backend.risklist.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.dreamparking.backend.risklist.entity.enums.RiskListDataType;

/**
 * Hash of a risk list value: HMAC-SHA256 keyed with the salt {@code app.risk-lists.salt}, over the data type and the
 * normalized value, as 64 lowercase hex characters. The same value always gives the same hash (so a request can be
 * looked up) and the type is part of the input (a DUI and a phone with the same digits do not collide).
 * <p>
 * A DUI or a phone number has few possible values, so the hash only protects them while the salt stays secret: it is
 * a Container App secret ({@code APP_RISK_LISTS_SALT}), never in the repository. Without it the application does not
 * start, except with the {@code dev} profile, which uses a fixed local value and warns. Changing the salt makes every
 * stored hash stop matching, so entries would have to be added again.
 */
@Component
public class RiskListHasher {

	private static final Logger log = LoggerFactory.getLogger(RiskListHasher.class);

	static final int MIN_SALT_LENGTH = 16;

	/** Public and only for local work: never a protection of real data. */
	static final String LOCAL_SALT = "local-development-salt-not-a-secret";

	private static final String ALGORITHM = "HmacSHA256";

	private final byte[] salt;

	public RiskListHasher(@Value("${app.risk-lists.salt:}") String salt, Environment environment) {
		this.salt = (salt.isBlank() ? localSalt(environment) : validated(salt)).getBytes(StandardCharsets.UTF_8);
	}

	/** The hash of an already normalized value (see {@link RiskListDataType#normalize}). */
	public String hash(RiskListDataType type, String normalizedValue) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(new SecretKeySpec(salt, ALGORITHM));
			mac.update(type.name().getBytes(StandardCharsets.UTF_8));
			mac.update((byte) ':');
			return HexFormat.of().formatHex(mac.doFinal(normalizedValue.getBytes(StandardCharsets.UTF_8)));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Cannot hash the risk list value", ex);
		}
	}

	private static String validated(String salt) {
		String trimmed = salt.trim();
		if (trimmed.length() < MIN_SALT_LENGTH) {
			throw new IllegalStateException(
					"app.risk-lists.salt (APP_RISK_LISTS_SALT) must have at least " + MIN_SALT_LENGTH + " characters");
		}
		return trimmed;
	}

	private static String localSalt(Environment environment) {
		if (!environment.matchesProfiles("dev")) {
			throw new IllegalStateException("app.risk-lists.salt (APP_RISK_LISTS_SALT) is required");
		}
		log.warn("APP_RISK_LISTS_SALT is not set: risk list values are hashed with a public local salt");
		return LOCAL_SALT;
	}

}
