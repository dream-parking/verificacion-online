package com.dreamparking.backend.console;

import java.util.UUID;

/** Console user as shown in the header and in alert assignment. */
public record ConsoleUserResponse(UUID id, String email, String fullName, String jobTitle, String initials,
		ConsoleRole role) {

	static ConsoleUserResponse of(ConsoleUser user) {
		return new ConsoleUserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getJobTitle(),
				user.getInitials(), user.getRole());
	}

}
