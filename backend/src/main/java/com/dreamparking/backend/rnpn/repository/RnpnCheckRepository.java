package com.dreamparking.backend.rnpn.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.rnpn.entity.RnpnCheck;

public interface RnpnCheckRepository extends JpaRepository<RnpnCheck, Long> {

	/** The last check of the request: the one that counts in the score and the console. */
	Optional<RnpnCheck> findFirstByRequestIdOrderByCheckedAtDescIdDesc(UUID requestId);

}
