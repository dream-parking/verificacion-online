package com.dreamparking.backend.dossier.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.customer.entity.Customer;
import com.dreamparking.backend.customer.repository.CustomerRepository;
import com.dreamparking.backend.dossier.dto.ArchivedDossierResponse;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.onboarding.repository.OnboardingRequestRepository;

/**
 * Finds archived binding dossiers by number (VDI-135), to answer a review of the regulator. A request has at most one
 * dossier, frozen when it signed; a customer has one per signed request (one per kind of account).
 */
@Service
public class DossierLookupService {

	private final OnboardingRequestRepository requests;

	private final CustomerRepository customers;

	private final BindingDossierService dossiers;

	public DossierLookupService(OnboardingRequestRepository requests, CustomerRepository customers,
			BindingDossierService dossiers) {
		this.requests = requests;
		this.customers = customers;
		this.dossiers = dossiers;
	}

	/**
	 * The dossier of the request.
	 * @throws NotFoundException when there is no request with that number, or it has not signed yet
	 */
	@Transactional(readOnly = true)
	public ArchivedDossierResponse byRequestNumber(String requestNumber) {
		OnboardingRequest request = requests.findByNumber(requestNumber)
			.orElseThrow(() -> new NotFoundException("Onboarding request not found: " + requestNumber));
		return archived(request).orElseThrow(() -> new NotFoundException(
				"Request " + requestNumber + " has no binding dossier: it has not signed the contract"));
	}

	/**
	 * Every dossier of the customer, the most recently signed first; empty when no request of the customer has signed.
	 * @throws NotFoundException when there is no customer with that number
	 */
	@Transactional(readOnly = true)
	public List<ArchivedDossierResponse> byCustomerNumber(String customerNumber) {
		Customer customer = customers.findByNumber(customerNumber)
			.orElseThrow(() -> new NotFoundException("Customer not found: " + customerNumber));
		return requests.findByCustomerIdOrderByStartedAtAscIdAsc(customer.getId())
			.stream()
			.map(this::archived)
			.flatMap(Optional::stream)
			.sorted(Comparator.comparing((ArchivedDossierResponse a) -> a.dossier().signature().signedAt()).reversed())
			.toList();
	}

	private Optional<ArchivedDossierResponse> archived(OnboardingRequest request) {
		return dossiers.find(request.getId())
			.map(dossier -> new ArchivedDossierResponse(request.getNumber(),
					request.getCustomer() == null ? null : request.getCustomer().getNumber(), dossier));
	}

}
