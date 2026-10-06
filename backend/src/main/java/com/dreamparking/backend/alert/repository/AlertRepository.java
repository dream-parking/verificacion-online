package com.dreamparking.backend.alert.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.alert.entity.Alert;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

}
