package com.dreamparking.backend.alert.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.alert.entity.AlertInboxItem;

public interface AlertInboxItemRepository extends JpaRepository<AlertInboxItem, UUID> {

	List<AlertInboxItem> findAllByOrderBySeverityAscRaisedAtDesc();

}
