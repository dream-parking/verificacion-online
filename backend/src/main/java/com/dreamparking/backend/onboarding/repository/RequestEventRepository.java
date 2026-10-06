package com.dreamparking.backend.onboarding.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.onboarding.entity.RequestEvent;

public interface RequestEventRepository extends JpaRepository<RequestEvent, Long> {

	List<RequestEvent> findByRequestIdOrderByOccurredAt(UUID requestId);

}
