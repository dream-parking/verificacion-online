package com.dreamparking.backend.account.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.account.entity.Account;

public interface AccountRepository extends JpaRepository<Account, UUID> {

}
