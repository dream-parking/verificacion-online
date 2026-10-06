package com.dreamparking.backend.onboarding.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.onboarding.entity.PrivacyConsent;

public interface PrivacyConsentRepository extends JpaRepository<PrivacyConsent, UUID> {

}
