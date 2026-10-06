package com.dreamparking.backend.alert.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.alert.entity.AlertType;

public interface AlertTypeRepository extends JpaRepository<AlertType, String> {

}
