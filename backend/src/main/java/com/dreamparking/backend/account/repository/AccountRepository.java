package com.dreamparking.backend.account.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.account.entity.Account;

public interface AccountRepository extends JpaRepository<Account, UUID> {

	List<Account> findByCustomerIdOrderByOpenedAt(UUID customerId);

	boolean existsByRequestId(UUID requestId);

	boolean existsByNumberToken(String numberToken);

}
