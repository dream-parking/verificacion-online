package com.dreamparking.backend.identity.ocr;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.dreamparking.backend.identity.entity.enums.UnreadableReason;

/**
 * What was read from the DUI. Every field is {@code null} when it could not be read with certainty; {@code dui} is
 * always in the {@code 00000000-0} format and {@code gender} is {@code M} or {@code F}.
 */
public record DuiReading(boolean readable, UnreadableReason unreadableReason, String dui, String firstNames,
		String lastNames, LocalDate birthDate, LocalDate issueDate, LocalDate expiryDate, String gender,
		Boolean looksAuthentic, BigDecimal confidence) {

	/** A reading is only useful when it has the DUI number and the names to pre-fill the basic data. */
	public boolean isComplete() {
		return readable && dui != null && firstNames != null && lastNames != null;
	}

}
