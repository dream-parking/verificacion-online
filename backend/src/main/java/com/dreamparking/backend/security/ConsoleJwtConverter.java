package com.dreamparking.backend.security;

import java.util.List;
import java.util.UUID;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.dreamparking.backend.console.ConsoleUser;
import com.dreamparking.backend.console.ConsoleUserRepository;

/**
 * Turns a verified token into the authenticated console user. The user and role are read from the database on each
 * request, so deactivating a user or changing their role takes effect immediately, not when the token expires.
 */
@Component
public class ConsoleJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	private final ConsoleUserRepository users;

	public ConsoleJwtConverter(ConsoleUserRepository users) {
		this.users = users;
	}

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		ConsoleUser user = parse(jwt.getSubject()).flatMap(users::findById)
			.filter(found -> Boolean.TRUE.equals(found.getActive()))
			.orElseThrow(() -> new BadCredentialsException("The user of this token does not exist or is inactive"));
		AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole());
		return UsernamePasswordAuthenticationToken.authenticated(principal, jwt,
				List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
	}

	private static java.util.Optional<UUID> parse(String subject) {
		try {
			return java.util.Optional.of(UUID.fromString(subject));
		}
		catch (IllegalArgumentException | NullPointerException ex) {
			return java.util.Optional.empty();
		}
	}

}
