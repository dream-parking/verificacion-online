package com.dreamparking.backend.account.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.dreamparking.backend.account.entity.Account;

public interface AccountRepository extends JpaRepository<Account, UUID> {

	List<Account> findByCustomerIdOrderByOpenedAt(UUID customerId);

	boolean existsByRequestId(UUID requestId);

	boolean existsByNumberToken(String numberToken);

	/** The account locked until the transaction ends, so the movements of an account are compared one at a time. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from Account a where a.id = :accountId")
	Optional<Account> lockById(UUID accountId);

}
