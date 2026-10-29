package com.dreamparking.backend.onboarding.entity.enums;

/** Where an analyst sends a request held for enhanced review to finish the verification (VDI-143). */
public enum EscalationChannel {

	/** The customer is verified in a video call. */
	VIDEO_CALL,

	/** The customer goes to a branch. */
	BRANCH;

	/** The channel as the console and the timeline say it. */
	public String label() {
		return this == VIDEO_CALL ? "videollamada" : "agencia";
	}

}
