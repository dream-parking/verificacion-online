package com.dreamparking.backend.catalog.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.catalog.entity.TransactionType;

public interface TransactionTypeRepository extends JpaRepository<TransactionType, String> {

	List<TransactionType> findByActiveTrueOrderBySortOrder();

}
