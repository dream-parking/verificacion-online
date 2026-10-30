package com.dreamparking.backend.product.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.product.entity.ProductRequest;

public interface ProductRequestRepository extends JpaRepository<ProductRequest, Long> {

	Optional<ProductRequest> findByRequestId(UUID requestId);

}
