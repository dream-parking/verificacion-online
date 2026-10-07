package com.dreamparking.backend.alert.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.alert.entity.AlertHistory;

public interface AlertHistoryRepository extends JpaRepository<AlertHistory, Long> {

	List<AlertHistory> findByAlertIdOrderByOccurredAt(UUID alertId);

}
