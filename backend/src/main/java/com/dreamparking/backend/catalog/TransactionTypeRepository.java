package com.dreamparking.backend.catalog;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionTypeRepository extends JpaRepository<TransactionType, String> {

	List<TransactionType> findByActiveTrueOrderBySortOrder();

}
