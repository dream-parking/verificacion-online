package com.dreamparking.backend.onboarding;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivacyConsentRepository extends JpaRepository<PrivacyConsent, UUID> {

}
