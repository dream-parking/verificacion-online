package com.dreamparking.backend.onboarding.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.onboarding.entity.RequestNumberSequence;

public interface RequestNumberSequenceRepository extends JpaRepository<RequestNumberSequence, Short> {

}
