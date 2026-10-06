package com.dreamparking.backend.account.dto;

import java.time.Instant;
import java.util.UUID;

import com.dreamparking.backend.account.entity.Account;
import com.dreamparking.backend.account.entity.enums.AccountStatus;

/** Account as shown outside the core: only the masked number, never the token. */
public record AccountResponse(UUID id, UUID customerId, UUID requestId, String maskedNumber, AccountStatus status,
		Instant openedAt) {

	public static AccountResponse of(Account account) {
		return new AccountResponse(account.getId(), account.getCustomer().getId(),
				account.getRequest() == null ? null : account.getRequest().getId(), "•••• " + account.getLastFour(),
				account.getStatus(), account.getOpenedAt());
	}

}
