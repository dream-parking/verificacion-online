package com.dreamparking.backend.risklist.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.risklist.entity.RiskList;

public interface RiskListRepository extends JpaRepository<RiskList, Short> {

	Optional<RiskList> findByCode(String code);

	List<RiskList> findAllByOrderByWeightDescCodeAsc();

}
