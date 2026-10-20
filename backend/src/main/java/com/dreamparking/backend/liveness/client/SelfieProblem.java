package com.dreamparking.backend.liveness.client;

/**
 * Why a selfie cannot be judged: only how it was taken, never why it looks fake (a screen, a printout…), so the
 * customer does not learn what the check looks for. The app shows its own message for each value.
 */
public enum SelfieProblem {

	BLURRY,
	TOO_DARK,
	GLARE,
	/** No human face, or it is cut off by the frame or covered. */
	FACE_NOT_VISIBLE,
	MULTIPLE_FACES,
	OTHER

}
