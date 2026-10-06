package com.dreamparking.backend.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Signing key, encoder and decoder of the console's access tokens (HS256). */
@Configuration
public class JwtConfig {

	static final String ISSUER = "verificacion-online";

	private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

	private static final int MIN_SECRET_BYTES = 32;

	@Bean
	SecretKey jwtSecretKey(@Value("${app.security.jwt-secret:}") String secret) {
		byte[] bytes;
		if (secret.isBlank()) {
			bytes = new byte[MIN_SECRET_BYTES];
			new SecureRandom().nextBytes(bytes);
			log.warn("APP_JWT_SECRET is not set: using a random signing key. Console sessions will not survive a restart.");
		}
		else {
			bytes = secret.getBytes(StandardCharsets.UTF_8);
			if (bytes.length < MIN_SECRET_BYTES) {
				throw new IllegalStateException("APP_JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes long");
			}
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(ISSUER)));
		return decoder;
	}

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	Duration tokenTtl(@Value("${app.security.token-ttl:8h}") Duration ttl) {
		return ttl;
	}

}
