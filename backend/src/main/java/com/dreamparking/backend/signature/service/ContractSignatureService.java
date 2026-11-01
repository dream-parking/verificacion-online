package com.dreamparking.backend.signature.service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.common.exception.InvalidStateException;
import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.dossier.dto.BindingDossierResponse;
import com.dreamparking.backend.dossier.service.BindingDossierService;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.onboarding.entity.RequestEvent;
import com.dreamparking.backend.onboarding.entity.RequestSignals;
import com.dreamparking.backend.onboarding.entity.enums.RequestEventType;
import com.dreamparking.backend.onboarding.entity.enums.RequestStatus;
import com.dreamparking.backend.onboarding.repository.OnboardingRequestRepository;
import com.dreamparking.backend.onboarding.repository.RequestEventRepository;
import com.dreamparking.backend.onboarding.repository.RequestSignalsRepository;
import com.dreamparking.backend.signature.client.ContractSigner;
import com.dreamparking.backend.signature.client.SigningQuery;
import com.dreamparking.backend.signature.client.SigningResult;
import com.dreamparking.backend.signature.dto.ContractSignatureResponse;
import com.dreamparking.backend.signature.entity.ContractSignature;
import com.dreamparking.backend.signature.repository.ContractSignatureRepository;

/**
 * Electronic signature of the account contract (VDI-130). Only a submitted and approved request ({@code COMPLETED})
 * signs, once, the contract version in force; the same rule as opening an account. The evidence is stored in an
 * append-only table and the timeline gets an event, also when the signature service rejects it. Signing also freezes the
 * binding dossier (VDI-131).
 */
@Service
public class ContractSignatureService {

	private final ContractSignatureRepository signatures;

	private final OnboardingRequestRepository requests;

	private final RequestSignalsRepository signals;

	private final RequestEventRepository events;

	private final ContractTerms terms;

	private final ContractSigner signer;

	private final BindingDossierService dossiers;

	public ContractSignatureService(ContractSignatureRepository signatures, OnboardingRequestRepository requests,
			RequestSignalsRepository signals, RequestEventRepository events, ContractTerms terms, ContractSigner signer,
			BindingDossierService dossiers) {
		this.signatures = signatures;
		this.requests = requests;
		this.signals = signals;
		this.events = events;
		this.terms = terms;
		this.signer = signer;
		this.dossiers = dossiers;
	}

	/**
	 * Signs the contract version the app showed. A rejection by the signature service is answered with 422 but still
	 * leaves its event on the timeline, so the transaction is not rolled back for it.
	 * @param ip the client address of this call, the IP of the session
	 * @param device the User-Agent of this call
	 */
	@Transactional(noRollbackFor = SignatureRejectedException.class)
	public ContractSignatureResponse sign(UUID requestId, String contractVersion, String ip, String device) {
		OnboardingRequest request = requests.findById(requestId)
			.orElseThrow(() -> new NotFoundException("No existe la solicitud " + requestId));
		checkCanSign(request);
		if (!terms.version().equals(contractVersion.trim())) {
			throw new InvalidStateException("La versión " + contractVersion.trim() + " del contrato no es la vigente ("
					+ terms.version() + "): muestra el contrato de nuevo y firma esa versión");
		}

		SigningResult result = signer.sign(new SigningQuery(requestId, request.getCustomer().getDui(), terms.version(),
				terms.fingerprint()));
		if (!result.signed()) {
			recordEvent(request, RequestEventType.CONTRACT_SIGNATURE_REJECTED, "Firma del contrato rechazada",
					Map.of("contractVersion", terms.version(), "provider", signer.provider(), "reason",
							result.rejectionReason()));
			throw new SignatureRejectedException(result.rejectionReason());
		}

		String deviceFingerprint = signals.findById(requestId).map(RequestSignals::getDeviceFingerprint).orElse(null);
		ContractSignature signature;
		try {
			signature = signatures.saveAndFlush(new ContractSignature(request, terms.version(), terms.fingerprint(),
					signer.provider(), addressOf(ip), device, deviceFingerprint));
		}
		catch (DataIntegrityViolationException ex) {
			// Two signatures of the same request at the same time: the unique request_id lets only one through.
			throw new InvalidStateException("La solicitud " + requestId + " ya firmó el contrato");
		}
		// VDI-131: the evidence that backed the approval is frozen with the signature, in the same transaction.
		dossiers.freeze(requestId, new BindingDossierResponse.Signature(terms.version(), terms.fingerprint(),
				signature.getSignedAt()));
		recordEvent(request, RequestEventType.CONTRACT_SIGNED, "Contrato firmado",
				Map.of("contractVersion", terms.version(), "contractFingerprint", terms.fingerprint(), "provider",
						signer.provider()));
		return ContractSignatureResponse.of(signature);
	}

	/** Same rule as {@code AccountService.open}: only an approved request, and only once. */
	private void checkCanSign(OnboardingRequest request) {
		if (request.getStatus() == RequestStatus.ENHANCED_REVIEW) {
			throw new InvalidStateException("La solicitud está en revisión reforzada y no puede firmar el contrato");
		}
		if (request.getStatus() == RequestStatus.AWAITING_RNPN) {
			throw new InvalidStateException("La solicitud espera el cotejo con el RNPN y no puede firmar el contrato");
		}
		if (request.getStatus() != RequestStatus.COMPLETED) {
			throw new InvalidStateException("Solo las solicitudes enviadas y aprobadas pueden firmar el contrato");
		}
		if (signatures.existsByRequestId(request.getId())) {
			throw new InvalidStateException("La solicitud " + request.getId() + " ya firmó el contrato");
		}
	}

	private void recordEvent(OnboardingRequest request, RequestEventType type, String description,
			Map<String, Object> data) {
		RequestEvent event = new RequestEvent();
		event.setRequest(request);
		event.setType(type);
		event.setDescription(description);
		event.setData(data);
		events.save(event);
	}

	private static InetAddress addressOf(String ip) {
		if (ip == null || ip.isBlank()) {
			return null;
		}
		try {
			return InetAddress.getByName(ip);
		}
		catch (UnknownHostException ex) {
			return null;
		}
	}

}
