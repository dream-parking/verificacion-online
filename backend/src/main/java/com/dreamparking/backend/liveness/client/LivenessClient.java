package com.dreamparking.backend.liveness.client;

import com.dreamparking.backend.identity.ocr.DocumentPhoto;

/** Tells how likely it is that a selfie shows a live person in front of the camera (VDI-114). */
public interface LivenessClient {

	/**
	 * @return the provider's confidence; a selfie that does not look live is a normal result (low confidence), not an error
	 * @throws LivenessException when the provider is off, fails or times out
	 */
	LivenessResult check(DocumentPhoto selfie) throws LivenessException;

	/** Name of the provider or model, stored with each check. */
	String provider();

}
