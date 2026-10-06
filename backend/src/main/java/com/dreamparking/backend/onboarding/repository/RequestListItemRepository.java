package com.dreamparking.backend.onboarding.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.dreamparking.backend.onboarding.entity.RequestListItem;

public interface RequestListItemRepository extends JpaRepository<RequestListItem, UUID>, JpaSpecificationExecutor<RequestListItem> {

}
