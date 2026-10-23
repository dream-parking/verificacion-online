package com.dreamparking.backend.rnpn.client;

import java.util.List;

import com.dreamparking.backend.rnpn.entity.enums.RnpnField;

/** What the RNPN answered: the data matches, or it does not and these fields differ. */
public record RnpnResult(List<RnpnField> mismatchedFields) {

	public RnpnResult {
		mismatchedFields = List.copyOf(mismatchedFields);
	}

	public static RnpnResult match() {
		return new RnpnResult(List.of());
	}

	public static RnpnResult noMatch(List<RnpnField> mismatchedFields) {
		if (mismatchedFields.isEmpty()) {
			throw new IllegalArgumentException("A result that does not match must say which fields differ");
		}
		return new RnpnResult(mismatchedFields);
	}

	public boolean matches() {
		return mismatchedFields.isEmpty();
	}

}
