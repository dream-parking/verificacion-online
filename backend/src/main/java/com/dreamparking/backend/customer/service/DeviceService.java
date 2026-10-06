package com.dreamparking.backend.customer.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.customer.entity.Device;
import com.dreamparking.backend.customer.repository.DeviceRepository;

@Service
public class DeviceService {

	private final DeviceRepository devices;

	public DeviceService(DeviceRepository devices) {
		this.devices = devices;
	}

	/**
	 * Device with this fingerprint, created the first time it is seen. Reusing the record across requests is
	 * what lets the console flag several requests from the same device.
	 */
	@Transactional
	public Device register(String fingerprint, String model, String operatingSystem) {
		Device device = devices.findByFingerprint(fingerprint).orElseGet(Device::new);
		device.setFingerprint(fingerprint);
		if (model != null) {
			device.setModel(model);
		}
		if (operatingSystem != null) {
			device.setOperatingSystem(operatingSystem);
		}
		device.setLastSeenAt(Instant.now());
		return devices.save(device);
	}

}
