package com.dreamparking.backend.catalog.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.catalog.entity.IncomeRange;

public interface IncomeRangeRepository extends JpaRepository<IncomeRange, String> {

	List<IncomeRange> findByActiveTrueOrderBySortOrder();

}
