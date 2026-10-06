package com.dreamparking.backend.onboarding.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.onboarding.entity.RequestSignals;

public interface RequestSignalsRepository extends JpaRepository<RequestSignals, UUID> {

}
