package com.dreamparking.backend.console.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.console.entity.ConsoleUser;
import com.dreamparking.backend.console.entity.enums.ConsoleRole;

public interface ConsoleUserRepository extends JpaRepository<ConsoleUser, UUID> {

	Optional<ConsoleUser> findByEmail(String email);

	Optional<ConsoleUser> findByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCase(String email);

	long countByRoleAndActiveTrue(ConsoleRole role);

}
