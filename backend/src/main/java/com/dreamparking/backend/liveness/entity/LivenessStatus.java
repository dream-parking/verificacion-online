package com.dreamparking.backend.liveness.entity;

/** Outcome of the liveness check of a request (VDI-114). */
public enum LivenessStatus {

	/** The provider answered; the confidence is its answer. */
	CHECKED,

	/** The provider is off or failed; there is no confidence and the signal does not count in the score. */
	FAILED

}
