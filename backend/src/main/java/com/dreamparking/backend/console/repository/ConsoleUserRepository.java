package com.dreamparking.backend.console.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.console.entity.ConsoleUser;

public interface ConsoleUserRepository extends JpaRepository<ConsoleUser, UUID> {

	Optional<ConsoleUser> findByEmail(String email);

	List<ConsoleUser> findAllByOrderByFullName();

	List<ConsoleUser> findByActiveTrueOrderByFullName();

	boolean existsByEmail(String email);

}
