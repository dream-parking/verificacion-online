package com.dreamparking.backend.console.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.console.entity.AccessAudit;

public interface AccessAuditRepository extends JpaRepository<AccessAudit, Long> {

}
