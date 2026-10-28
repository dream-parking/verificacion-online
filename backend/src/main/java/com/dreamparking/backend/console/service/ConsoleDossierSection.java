package com.dreamparking.backend.console.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.console.dto.ConsoleRequestDetail;
import com.dreamparking.backend.dossier.dto.BindingDossierResponse;
import com.dreamparking.backend.dossier.service.BindingDossierService;
import com.dreamparking.backend.identity.entity.IdentityDocument;
import com.dreamparking.backend.identity.entity.enums.OcrStatus;
import com.dreamparking.backend.identity.repository.IdentityDocumentImageRepository;
import com.dreamparking.backend.identity.repository.IdentityDocumentRepository;
import com.dreamparking.backend.identity.service.DuiValidator;
import com.dreamparking.backend.onboarding.repository.PrivacyConsentRepository;
import com.dreamparking.backend.risk.dto.RiskAssessmentResponse;
import com.dreamparking.backend.risk.dto.RiskDecisionResponse;
import com.dreamparking.backend.risk.service.RiskAssessmentService;
import com.dreamparking.backend.risk.service.RiskDecisionService;
import com.dreamparking.backend.rnpn.repository.RnpnCheckRepository;

/**
 * The part of a request's detail that is the evidence behind an approval: the privacy consent, the DUI, the RNPN check,
 * the risk assessment and the log of decisions, plus the binding dossier frozen when the customer signed (VDI-131).
 * It comes out of {@link ConsoleRequestService#detail} (VDI-126) so the
 * stories that work on the binding file (VDI-131, VDI-134) change this class and not the whole detail.
 */
@Component
@Transactional(readOnly = true)
class ConsoleDossierSection {

	/** Dates printed on the DUI are Salvadoran dates. */
	private static final ZoneId EL_SALVADOR = ZoneId.of("America/El_Salvador");

	private final PrivacyConsentRepository consents;

	private final IdentityDocumentRepository identityDocuments;

	private final IdentityDocumentImageRepository identityDocumentImages;

	private final RnpnCheckRepository rnpnChecks;

	private final RiskAssessmentService riskAssessments;

	private final RiskDecisionService riskDecisions;

	private final BindingDossierService bindingDossiers;

	ConsoleDossierSection(PrivacyConsentRepository consents, IdentityDocumentRepository identityDocuments,
			IdentityDocumentImageRepository identityDocumentImages, RnpnCheckRepository rnpnChecks,
			RiskAssessmentService riskAssessments, RiskDecisionService riskDecisions,
			BindingDossierService bindingDossiers) {
		this.consents = consents;
		this.identityDocuments = identityDocuments;
		this.identityDocumentImages = identityDocumentImages;
		this.rnpnChecks = rnpnChecks;
		this.riskAssessments = riskAssessments;
		this.riskDecisions = riskDecisions;
		this.bindingDossiers = bindingDossiers;
	}

	/** What the detail shows of the file; each part is null (the decisions, empty) until the request reaches it. */
	record Section(ConsoleRequestDetail.Consent consent, ConsoleRequestDetail.IdentityDocument identityDocument,
			ConsoleRequestDetail.Rnpn rnpn, RiskAssessmentResponse risk, List<RiskDecisionResponse> riskDecisions,
			BindingDossierResponse bindingDossier) {
	}

	Section of(UUID requestId) {
		var consent = consents.findById(requestId)
			.map(c -> new ConsoleRequestDetail.Consent(c.getNotice().getVersion(), c.getAcceptedAt(),
					Boolean.TRUE.equals(c.getSignalsAccepted()), c.getTermsAccepted()))
			.orElse(null);
		var identityDocument = identityDocuments.findById(requestId).map(this::toIdentityDocument).orElse(null);
		var rnpn = rnpnChecks.findFirstByRequestIdOrderByCheckedAtDescIdDesc(requestId)
			.map(c -> new ConsoleRequestDetail.Rnpn(c.getOutcome(), c.isSimulated(), c.getProvider(), c.getAttempts(),
					c.getMismatchedFields(), c.getCheckedAt()))
			.orElse(null);
		var risk = riskAssessments.findCurrent(requestId).orElse(null);
		return new Section(consent, identityDocument, rnpn, risk, riskDecisions.log(requestId),
				bindingDossiers.find(requestId).orElse(null));
	}

	private ConsoleRequestDetail.IdentityDocument toIdentityDocument(IdentityDocument d) {
		var read = d.getStatus() == OcrStatus.READ ? new ConsoleRequestDetail.DuiData(d.getDui(), d.getFirstNames(),
				d.getLastNames(), d.getBirthDate(), d.getIssueDate(), d.getExpiryDate(), d.getGender()) : null;
		var confirmed = d.isConfirmed() ? new ConsoleRequestDetail.DuiData(d.getConfirmedDui(),
				d.getConfirmedFirstNames(), d.getConfirmedLastNames(), d.getConfirmedBirthDate(), null,
				d.getConfirmedExpiryDate(), null) : null;
		String dui = d.isConfirmed() ? d.getConfirmedDui() : d.getDui();
		return new ConsoleRequestDetail.IdentityDocument(d.getStatus(), d.getModel(), d.getAttempts(),
				d.getUnreadableReason(), d.getUnreadableSide(), d.getFailure(), read, confirmed, d.getConfirmedAt(),
				d.getCorrectedFields(), d.getDuiMatchesDeclared(), d.getNamesMatchDeclared(),
				dui == null ? null : DuiValidator.hasValidCheckDigit(dui),
				d.effectiveExpiryDate() == null ? null : d.isExpired(LocalDate.now(EL_SALVADOR)), d.getLooksAuthentic(),
				d.getConfidence(), identityDocumentImages.findSidesByRequestId(d.getRequestId()), d.getProcessedAt(),
				d.getRejectedConfirmations(), d.getRejectionReasons(), d.isEverLookedInauthentic());
	}

}
