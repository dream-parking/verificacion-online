package com.dreamparking.backend.account.dto;

import jakarta.validation.constraints.NotNull;

import com.dreamparking.backend.account.entity.enums.AccountStatus;

public record ChangeAccountStatusRequest(@NotNull AccountStatus status) {
}
