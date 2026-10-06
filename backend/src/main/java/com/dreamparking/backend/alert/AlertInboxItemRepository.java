package com.dreamparking.backend.alert;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AlertInboxItemRepository extends JpaRepository<AlertInboxItem, UUID>, JpaSpecificationExecutor<AlertInboxItem> {

	List<AlertInboxItem> findAllByOrderBySeverityAscRaisedAtDesc();

}
