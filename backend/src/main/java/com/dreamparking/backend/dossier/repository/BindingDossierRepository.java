package com.dreamparking.backend.dossier.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.dossier.entity.BindingDossier;

public interface BindingDossierRepository extends JpaRepository<BindingDossier, Long> {

	Optional<BindingDossier> findByRequest_Id(UUID requestId);

}
