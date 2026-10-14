package com.dreamparking.backend.account.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.dreamparking.backend.account.entity.AccountMovement;
import com.dreamparking.backend.account.entity.enums.MovementDirection;

/** A registered movement of an account. */
public record MovementResponse(UUID id, UUID accountId, String externalId, MovementDirection direction,
		BigDecimal amountUsd, String transactionTypeCode, String description, Instant occurredAt,
		Instant registeredAt) {

	public static MovementResponse of(AccountMovement movement) {
		return new MovementResponse(movement.getId(), movement.getAccount().getId(), movement.getExternalId(),
				movement.getDirection(), movement.getAmountUsd(),
				movement.getTransactionType() == null ? null : movement.getTransactionType().getCode(),
				movement.getDescription(), movement.getOccurredAt(), movement.getRegisteredAt());
	}

}
