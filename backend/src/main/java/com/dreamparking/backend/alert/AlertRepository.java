package com.dreamparking.backend.alert;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

	/**
	 * Atomically assigns an unassigned alert to the user and records the change in its history (database function
	 * {@code tomar_alerta}). Returns false when the alert was already taken or closed.
	 */
	@Query(value = "select tomar_alerta(:alertId, :userId)", nativeQuery = true)
	boolean take(UUID alertId, UUID userId);

}
