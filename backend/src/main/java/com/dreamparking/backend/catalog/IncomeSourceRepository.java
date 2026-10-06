package com.dreamparking.backend.catalog;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IncomeSourceRepository extends JpaRepository<IncomeSource, String> {

	List<IncomeSource> findByActiveTrueOrderBySortOrder();

}
