package com.dreamparking.backend.console;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsoleUserRepository extends JpaRepository<ConsoleUser, UUID> {

	Optional<ConsoleUser> findByEmail(String email);

	Optional<ConsoleUser> findByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCase(String email);

	long countByRoleAndActiveTrue(ConsoleRole role);

}
