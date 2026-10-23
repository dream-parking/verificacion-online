package com.dreamparking.backend.rnpn.entity.enums;

/** What the RNPN check ended in. Same values as the {@code outcome} check of {@code rnpn_check}. */
public enum RnpnOutcome {

	/** The RNPN holds the same data as the DUI the customer confirmed. */
	MATCH,

	/** The RNPN answered and some of the data differs; see the mismatched fields. */
	NO_MATCH,

	/** The RNPN did not answer, even after the retries. */
	UNAVAILABLE

}
