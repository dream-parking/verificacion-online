package com.dreamparking.backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Who can call what. The mobile onboarding flow and the catalogs stay public (the app has no user to sign in; its
 * device-bound sign-in is VDI-4). Everything under {@code /api/console} needs a console token, and a few actions
 * need a specific role. Anything not listed here is denied.
 */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ConsoleJwtConverter jwtConverter) throws Exception {
		http.csrf(AbstractHttpConfigurer::disable) // stateless bearer tokens, no cookies
			.cors(Customizer.withDefaults())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(requests -> requests
				.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
				.requestMatchers("/error").permitAll()
				.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
				.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
				.requestMatchers("/api/catalogs", "/api/onboarding/**").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/console/auth/login").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/console/users", "/api/console/users/*/password")
				.hasRole("ADMIN")
				.requestMatchers(HttpMethod.PUT, "/api/console/users/*").hasRole("ADMIN")
				.requestMatchers(HttpMethod.POST, "/api/console/alerts/*/take").hasAnyRole("FRAUD_ANALYST", "ADMIN")
				.requestMatchers("/api/console/admin/**").hasRole("ADMIN")
				.requestMatchers("/api/console/**").authenticated()
				.anyRequest().denyAll())
			.oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
				.authenticationEntryPoint(ProblemJsonHandlers.UNAUTHORIZED)
				.accessDeniedHandler(ProblemJsonHandlers.FORBIDDEN))
			.exceptionHandling(errors -> errors.authenticationEntryPoint(ProblemJsonHandlers.UNAUTHORIZED)
				.accessDeniedHandler(ProblemJsonHandlers.FORBIDDEN));
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
