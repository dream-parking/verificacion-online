package com.dreamparking.backend.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.dreamparking.backend.console.ConsoleUser;

/** Issues the access token a console user gets after signing in. */
@Service
public class TokenService {

	private final JwtEncoder encoder;

	private final Clock clock;

	private final Duration ttl;

	public TokenService(JwtEncoder encoder, Clock clock, Duration tokenTtl) {
		this.encoder = encoder;
		this.clock = clock;
		this.ttl = tokenTtl;
	}

	/** Signed token whose subject is the user id; role and status are read from the database on every request. */
	public IssuedToken issue(ConsoleUser user) {
		Instant now = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(JwtConfig.ISSUER)
			.subject(user.getId().toString())
			.issuedAt(now)
			.expiresAt(now.plus(ttl))
			.build();
		String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
			.getTokenValue();
		return new IssuedToken(token, ttl.toSeconds());
	}

	public record IssuedToken(String value, long expiresInSeconds) {
	}

}
