package com.dreamparking.backend.alert.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.dreamparking.backend.alert.entity.AlertInboxItem;

public interface AlertInboxItemRepository
		extends JpaRepository<AlertInboxItem, UUID>, JpaSpecificationExecutor<AlertInboxItem> {

}
