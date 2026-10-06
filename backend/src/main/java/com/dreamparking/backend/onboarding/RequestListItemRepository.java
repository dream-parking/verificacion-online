package com.dreamparking.backend.onboarding;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RequestListItemRepository extends JpaRepository<RequestListItem, UUID>, JpaSpecificationExecutor<RequestListItem> {

}
