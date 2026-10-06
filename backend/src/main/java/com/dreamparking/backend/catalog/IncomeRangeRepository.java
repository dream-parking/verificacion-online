package com.dreamparking.backend.catalog;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IncomeRangeRepository extends JpaRepository<IncomeRange, String> {

	List<IncomeRange> findByActiveTrueOrderBySortOrder();

}
