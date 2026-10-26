package com.dreamparking.backend.dossier.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dreamparking.backend.liveness.entity.LivenessStatus;
import com.dreamparking.backend.risk.dto.RiskAssessmentResponse;
import com.dreamparking.backend.risk.dto.RiskDecisionResponse;
import com.dreamparking.backend.rnpn.entity.enums.RnpnField;
import com.dreamparking.backend.rnpn.entity.enums.RnpnOutcome;

/**
 * The binding dossier (VDI-131): what backed the approval of the request, frozen when the customer signed. Each element
 * carries the date and time it happened, so the dossier says which signals backed the approval and when, even if the
 * score model or its thresholds change later. An element is null when the request never had it.
 */
public record BindingDossierResponse(Long id, UUID requestId, Signature signature,
		@Schema(description = "Cuándo se congeló el expediente") Instant frozenAt,
		@Schema(description = "Hasta cuándo se retiene (VDI-134): la fecha de la firma más el plazo de retención; antes de ella no se puede borrar") Instant retainedUntil,
		Evidence evidence) {

	/** The signature: the contract version and the SHA-256 fingerprint (hex) of the text that was signed. */
	@Schema(name = "DossierSignature")
	public record Signature(String contractVersion, String contractFingerprint, Instant signedAt) {
	}

	/**
	 * The frozen evidence, stored as JSON. {@code schemaVersion} is 1; a change of shape raises it, so old dossiers
	 * stay readable.
	 */
	@Schema(name = "DossierEvidence")
	public record Evidence(int schemaVersion, Identity identity, Consent consent, Rnpn rnpn, Liveness liveness,
			Risk risk, List<RiskDecisionResponse> decisions) {
	}

	/** The DUI data the customer confirmed and the customer number (VDI-122, null until the first submission). */
	@Schema(name = "DossierIdentity")
	public record Identity(String customerNumber, String dui, String firstNames, String lastNames, LocalDate birthDate,
			LocalDate expiryDate, Instant confirmedAt) {
	}

	/** The privacy notice accepted (VDI-93): its version and when. */
	@Schema(name = "DossierConsent")
	public record Consent(String noticeVersion, boolean signalsAccepted, Boolean termsAccepted, Instant acceptedAt) {
	}

	/** The last check against the RNPN (VDI-111). */
	@Schema(name = "DossierRnpn")
	public record Rnpn(RnpnOutcome outcome, boolean simulated, String provider, short attempts,
			List<RnpnField> mismatchedFields, Instant checkedAt) {
	}

	/** The liveness check (VDI-114): the provider's confidence, from 0 to 1, null when it did not answer. */
	@Schema(name = "DossierLiveness")
	public record Liveness(LivenessStatus status, String provider, BigDecimal confidence, short attempts,
			Instant checkedAt) {
	}

	/**
	 * The score with each signal's contribution (the assessment as it was scored) and the thresholds of the model that
	 * scored it: up to {@code lowMax} is low risk, up to {@code mediumMax} medium, above that high.
	 */
	@Schema(name = "DossierRisk")
	public record Risk(RiskAssessmentResponse assessment, Short lowMax, Short mediumMax, Short reviewThreshold) {
	}

}
