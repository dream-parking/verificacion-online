package com.dreamparking.backend.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.catalog.entity.PrivacyNotice;

public interface PrivacyNoticeRepository extends JpaRepository<PrivacyNotice, Short> {

}
