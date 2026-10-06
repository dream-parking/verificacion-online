package com.dreamparking.backend.alert.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.dreamparking.backend.alert.entity.Alert;
import com.dreamparking.backend.alert.entity.enums.AlertStatus;
import com.dreamparking.backend.console.entity.ConsoleUser;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

	/**
	 * Assigns the alert only if nobody has taken it yet, in a single statement, so two analysts cannot take
	 * the same alert. Returns 0 when it was already taken.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Alert a
			set a.status = :assigned, a.assignee = :user, a.assignedAt = :now, a.version = a.version + 1
			where a.id = :alertId and a.status = :unassigned
			""")
	int takeIfUnassigned(UUID alertId, ConsoleUser user, Instant now, AlertStatus assigned, AlertStatus unassigned);

}
