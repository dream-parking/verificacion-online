package com.dreamparking.backend.identity.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.dreamparking.backend.identity.entity.IdentityDocumentImage;
import com.dreamparking.backend.identity.entity.enums.DocumentSide;

public interface IdentityDocumentImageRepository extends JpaRepository<IdentityDocumentImage, UUID> {

	Optional<IdentityDocumentImage> findByRequestIdAndSide(UUID requestId, DocumentSide side);

	/** Sides on file, without loading the photos. */
	@Query("select i.side from IdentityDocumentImage i where i.request.id = :requestId order by i.side")
	List<DocumentSide> findSidesByRequestId(UUID requestId);

}
