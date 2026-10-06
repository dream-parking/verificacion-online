package com.dreamparking.backend.alert;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertInboxItemRepository extends JpaRepository<AlertInboxItem, UUID> {

	List<AlertInboxItem> findAllByOrderBySeverityAscRaisedAtDesc();

}
