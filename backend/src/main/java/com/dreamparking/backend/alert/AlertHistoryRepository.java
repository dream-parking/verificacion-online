package com.dreamparking.backend.alert;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertHistoryRepository extends JpaRepository<AlertHistory, Long> {

	List<AlertHistory> findByAlertIdOrderByOccurredAt(UUID alertId);

}
