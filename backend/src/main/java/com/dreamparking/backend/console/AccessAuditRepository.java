package com.dreamparking.backend.console;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessAuditRepository extends JpaRepository<AccessAudit, Long> {

}
