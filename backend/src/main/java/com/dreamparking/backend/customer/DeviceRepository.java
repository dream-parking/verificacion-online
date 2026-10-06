package com.dreamparking.backend.customer;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

	Optional<Device> findByFingerprint(String fingerprint);

}
