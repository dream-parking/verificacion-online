package com.dreamparking.backend.identity.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.identity.entity.IdentityDocument;

public interface IdentityDocumentRepository extends JpaRepository<IdentityDocument, UUID> {

}
