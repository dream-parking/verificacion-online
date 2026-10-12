package com.dreamparking.backend.account.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.account.entity.ProfileDeviation;
import com.dreamparking.backend.account.entity.ProfileDeviationId;

public interface ProfileDeviationRepository extends JpaRepository<ProfileDeviation, ProfileDeviationId> {

	List<ProfileDeviation> findByIdAccountIdAndIdPeriod(UUID accountId, LocalDate period);

}
