package com.dreamparking.backend.catalog.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.catalog.entity.MonthlyAmountRange;

public interface MonthlyAmountRangeRepository extends JpaRepository<MonthlyAmountRange, String> {

	List<MonthlyAmountRange> findByActiveTrueOrderBySortOrder();

}
