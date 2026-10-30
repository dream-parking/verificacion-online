package com.dreamparking.backend.product.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.product.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Short> {

	List<Product> findByActiveTrueOrderByIdAsc();

	Optional<Product> findByCode(String code);

}
