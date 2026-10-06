package com.dreamparking.backend.onboarding.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.onboarding.entity.RequestListItem;

public interface RequestListItemRepository extends JpaRepository<RequestListItem, UUID> {

	List<RequestListItem> findAllByOrderByDateDesc();

}
