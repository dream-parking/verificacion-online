package com.dreamparking.backend.console.dto;

import java.util.UUID;

import com.dreamparking.backend.console.entity.ConsoleUser;
import com.dreamparking.backend.console.entity.enums.ConsoleRole;

public record ConsoleUserResponse(UUID id, String email, String fullName, String jobTitle, String initials,
		ConsoleRole role, boolean active) {

	public static ConsoleUserResponse of(ConsoleUser user) {
		return new ConsoleUserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getJobTitle(),
				user.getInitials(), user.getRole(), user.getActive());
	}

}
