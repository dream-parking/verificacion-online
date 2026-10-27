package com.dreamparking.backend.signature.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.signature.entity.ContractSignature;

public interface ContractSignatureRepository extends JpaRepository<ContractSignature, Long> {

	boolean existsByRequestId(UUID requestId);

	Optional<ContractSignature> findByRequestId(UUID requestId);

}
