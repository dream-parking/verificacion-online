package com.dreamparking.backend.catalog.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.catalog.entity.IncomeSource;

public interface IncomeSourceRepository extends JpaRepository<IncomeSource, String> {

	List<IncomeSource> findByActiveTrueOrderBySortOrder();

}
