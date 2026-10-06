package com.dreamparking.backend.security;

import java.util.UUID;

import com.dreamparking.backend.console.ConsoleRole;

/** The console user behind a valid token, with the role the database has right now. */
public record AuthenticatedUser(UUID id, String email, ConsoleRole role) {

	public boolean isAdmin() {
		return role == ConsoleRole.ADMIN;
	}

}
