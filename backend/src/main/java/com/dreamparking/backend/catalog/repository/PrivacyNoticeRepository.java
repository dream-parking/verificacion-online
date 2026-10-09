package com.dreamparking.backend.catalog.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.catalog.entity.PrivacyNotice;

public interface PrivacyNoticeRepository extends JpaRepository<PrivacyNotice, Short> {

	/** Notice in force at the given moment (the newest one already valid and not retired). */
	Optional<PrivacyNotice> findFirstByValidToIsNullAndValidFromLessThanEqualOrderByValidFromDesc(Instant now);

	List<PrivacyNotice> findAllByOrderByValidFromDesc();

	boolean existsByVersion(String version);

}
