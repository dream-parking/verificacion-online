package com.dreamparking.backend.customer.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.customer.entity.Device;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

	Optional<Device> findByFingerprint(String fingerprint);

}
