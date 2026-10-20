package com.dreamparking.backend.liveness.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.liveness.entity.LivenessCheck;

public interface LivenessCheckRepository extends JpaRepository<LivenessCheck, UUID> {

}
