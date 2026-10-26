package com.dreamparking.backend.dossier.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.common.exception.InvalidInputException;
import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.dossier.dto.BindingDossierResponse;
import com.dreamparking.backend.dossier.dto.BindingDossierResponse.Evidence;
import com.dreamparking.backend.dossier.dto.BindingDossierResponse.Signature;
import com.dreamparking.backend.dossier.entity.BindingDossier;
import com.dreamparking.backend.dossier.repository.BindingDossierRepository;
import com.dreamparking.backend.identity.entity.IdentityDocument;
import com.dreamparking.backend.identity.repository.IdentityDocumentRepository;
import com.dreamparking.backend.liveness.repository.LivenessCheckRepository;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.onboarding.repository.OnboardingRequestRepository;
import com.dreamparking.backend.onboarding.repository.PrivacyConsentRepository;
import com.dreamparking.backend.risk.dto.RiskAssessmentResponse;
import com.dreamparking.backend.risk.entity.ScoreModel;
import com.dreamparking.backend.risk.repository.ScoreModelRepository;
import com.dreamparking.backend.risk.service.RiskAssessmentService;
import com.dreamparking.backend.risk.service.RiskDecisionService;
import com.dreamparking.backend.rnpn.repository.RnpnCheckRepository;

/**
 * Builds and reads the binding dossier (VDI-131): when the customer signs, {@link #freeze} copies the evidence that
 * backed the approval into a row nobody can change. The electronic signature (VDI-15) calls it in the same transaction
 * that records the signature, with the contract version and fingerprint it signed:
 * {@code dossiers.freeze(requestId, new Signature(version, fingerprint, signedAt))}.
 */
@Service
public class BindingDossierService {

	/** Shape of the stored evidence; a change of shape raises it so old dossiers stay readable. */
	static final int SCHEMA_VERSION = 1;

	private static final Pattern FINGERPRINT = Pattern.compile("[0-9a-f]{64}");

	private static final int MAX_CONTRACT_VERSION = 30;

	private final BindingDossierRepository dossiers;

	private final OnboardingRequestRepository requests;

	private final PrivacyConsentRepository consents;

	private final IdentityDocumentRepository identityDocuments;

	private final RnpnCheckRepository rnpnChecks;

	private final LivenessCheckRepository livenessChecks;

	private final RiskAssessmentService riskAssessments;

	private final RiskDecisionService riskDecisions;

	private final ScoreModelRepository scoreModels;

	private final JsonMapper json;

	public BindingDossierService(BindingDossierRepository dossiers, OnboardingRequestRepository requests,
			PrivacyConsentRepository consents, IdentityDocumentRepository identityDocuments,
			RnpnCheckRepository rnpnChecks, LivenessCheckRepository livenessChecks,
			RiskAssessmentService riskAssessments, RiskDecisionService riskDecisions, ScoreModelRepository scoreModels,
			JsonMapper json) {
		this.dossiers = dossiers;
		this.requests = requests;
		this.consents = consents;
		this.identityDocuments = identityDocuments;
		this.rnpnChecks = rnpnChecks;
		this.livenessChecks = livenessChecks;
		this.riskAssessments = riskAssessments;
		this.riskDecisions = riskDecisions;
		this.scoreModels = scoreModels;
		this.json = json;
	}

	/**
	 * Freezes the dossier of the request as it is now, with the signature. Signing twice does not write a second
	 * dossier: the first one is returned, since it is the one that backed the approval.
	 */
	@Transactional
	public BindingDossierResponse freeze(UUID requestId, Signature signature) {
		validate(signature);
		Optional<BindingDossier> existing = dossiers.findByRequest_Id(requestId);
		if (existing.isPresent()) {
			return toResponse(existing.get());
		}
		OnboardingRequest request = requests.findById(requestId)
			.orElseThrow(() -> new NotFoundException("Onboarding request not found: " + requestId));
		String evidence = json.writeValueAsString(evidenceOf(request));
		return toResponse(dossiers.save(new BindingDossier(request, signature.contractVersion(),
				signature.contractFingerprint(), signature.signedAt(), evidence)));
	}

	/** The dossier of the request, or empty while the customer has not signed. */
	@Transactional(readOnly = true)
	public Optional<BindingDossierResponse> find(UUID requestId) {
		return dossiers.findByRequest_Id(requestId).map(this::toResponse);
	}

	private Evidence evidenceOf(OnboardingRequest request) {
		UUID id = request.getId();
		var identity = identityDocuments.findById(id).filter(IdentityDocument::isConfirmed)
			.map(d -> new BindingDossierResponse.Identity(
					request.getCustomer() == null ? null : request.getCustomer().getNumber(), d.getConfirmedDui(),
					d.getConfirmedFirstNames(), d.getConfirmedLastNames(), d.getConfirmedBirthDate(),
					d.getConfirmedExpiryDate(), d.getConfirmedAt()))
			.orElse(null);
		var consent = consents.findById(id)
			.map(c -> new BindingDossierResponse.Consent(c.getNotice().getVersion(),
					Boolean.TRUE.equals(c.getSignalsAccepted()), c.getTermsAccepted(), c.getAcceptedAt()))
			.orElse(null);
		var rnpn = rnpnChecks.findFirstByRequestIdOrderByCheckedAtDescIdDesc(id)
			.map(c -> new BindingDossierResponse.Rnpn(c.getOutcome(), c.isSimulated(), c.getProvider(), c.getAttempts(),
					c.getMismatchedFields(), c.getCheckedAt()))
			.orElse(null);
		var liveness = livenessChecks.findById(id)
			.map(c -> new BindingDossierResponse.Liveness(c.getStatus(), c.getProvider(), c.getConfidence(),
					c.getAttempts(), c.getCheckedAt()))
			.orElse(null);
		var risk = riskAssessments.findCurrent(id).map(this::riskOf).orElse(null);
		return new Evidence(SCHEMA_VERSION, identity, consent, rnpn, liveness, risk, riskDecisions.log(id));
	}

	/** The assessment with the thresholds of the model that scored it, which stay on the dossier if they change. */
	private BindingDossierResponse.Risk riskOf(RiskAssessmentResponse assessment) {
		var model = assessment.modelVersion() == null ? Optional.<ScoreModel>empty()
				: scoreModels.findById(assessment.modelVersion());
		return new BindingDossierResponse.Risk(assessment, model.map(ScoreModel::getLowMax).orElse(null),
				model.map(ScoreModel::getMediumMax).orElse(null), model.map(ScoreModel::reviewThreshold).orElse(null));
	}

	private BindingDossierResponse toResponse(BindingDossier dossier) {
		return new BindingDossierResponse(dossier.getId(), dossier.getRequest().getId(),
				new Signature(dossier.getContractVersion(), dossier.getContractFingerprint(), dossier.getSignedAt()),
				dossier.getFrozenAt(), json.readValue(dossier.getEvidence(), Evidence.class));
	}

	private static void validate(Signature signature) {
		if (signature == null || signature.signedAt() == null || signature.contractVersion() == null
				|| signature.contractVersion().isBlank() || signature.contractVersion().length() > MAX_CONTRACT_VERSION) {
			throw new InvalidInputException("The signature needs the contract version and the date and time it was signed");
		}
		if (signature.contractFingerprint() == null || !FINGERPRINT.matcher(signature.contractFingerprint()).matches()) {
			throw new InvalidInputException("The contract fingerprint must be a SHA-256 in lowercase hexadecimal");
		}
	}

}
