package com.dreamparking.backend.onboarding;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestEventRepository extends JpaRepository<RequestEvent, Long> {

	List<RequestEvent> findByRequestIdOrderByOccurredAt(UUID requestId);

}
