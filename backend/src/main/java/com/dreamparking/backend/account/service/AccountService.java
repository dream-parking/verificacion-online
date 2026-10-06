package com.dreamparking.backend.account.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.account.dto.AccountResponse;
import com.dreamparking.backend.account.dto.OpenAccountRequest;
import com.dreamparking.backend.account.entity.Account;
import com.dreamparking.backend.account.entity.enums.AccountStatus;
import com.dreamparking.backend.account.repository.AccountRepository;
import com.dreamparking.backend.common.exception.InvalidStateException;
import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.onboarding.entity.enums.RequestStatus;
import com.dreamparking.backend.onboarding.repository.OnboardingRequestRepository;

@Service
public class AccountService {

	private final AccountRepository accounts;

	private final OnboardingRequestRepository requests;

	public AccountService(AccountRepository accounts, OnboardingRequestRepository requests) {
		this.accounts = accounts;
		this.requests = requests;
	}

	/** Records the account opened for a submitted request. Each request opens at most one account. */
	@Transactional
	public AccountResponse open(OpenAccountRequest body) {
		OnboardingRequest request = requests.findById(body.requestId())
			.orElseThrow(() -> new NotFoundException("Onboarding request not found: " + body.requestId()));
		if (request.getStatus() != RequestStatus.COMPLETED) {
			throw new InvalidStateException("Only submitted requests can open an account");
		}
		if (accounts.existsByRequestId(request.getId())) {
			throw new InvalidStateException("Request " + request.getId() + " already opened an account");
		}
		if (accounts.existsByNumberToken(body.numberToken())) {
			throw new InvalidStateException("The account token is already registered");
		}

		Account account = new Account();
		account.setCustomer(request.getCustomer());
		account.setRequest(request);
		account.setNumberToken(body.numberToken());
		account.setLastFour(body.lastFour());
		return AccountResponse.of(accounts.saveAndFlush(account));
	}

	@Transactional(readOnly = true)
	public AccountResponse get(UUID accountId) {
		return AccountResponse.of(find(accountId));
	}

	@Transactional(readOnly = true)
	public List<AccountResponse> byCustomer(UUID customerId) {
		return accounts.findByCustomerIdOrderByOpenedAt(customerId).stream().map(AccountResponse::of).toList();
	}

	/** Blocks, reactivates or closes an account. A closed account is final. */
	@Transactional
	public AccountResponse changeStatus(UUID accountId, AccountStatus status) {
		Account account = find(accountId);
		if (account.getStatus() == AccountStatus.CLOSED && status != AccountStatus.CLOSED) {
			throw new InvalidStateException("Account " + accountId + " is closed");
		}
		account.setStatus(status);
		return AccountResponse.of(account);
	}

	/** Account entity for other modules (e.g. raising an alert on it). */
	@Transactional(readOnly = true)
	public Account find(UUID accountId) {
		return accounts.findById(accountId).orElseThrow(() -> new NotFoundException("Account not found: " + accountId));
	}

}
