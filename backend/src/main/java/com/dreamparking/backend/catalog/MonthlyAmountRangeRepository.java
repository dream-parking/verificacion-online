package com.dreamparking.backend.catalog;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyAmountRangeRepository extends JpaRepository<MonthlyAmountRange, String> {

	List<MonthlyAmountRange> findByActiveTrueOrderBySortOrder();

}
