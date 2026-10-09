package com.dreamparking.backend.identity.entity.enums;

/** Outcome of reading the DUI photos (VDI-79). */
public enum OcrStatus {

	/** The model read the document; the step advances. */
	READ,

	/** The photos cannot be read; the customer has to take them again and the step does not advance. */
	UNREADABLE,

	/** The provider is off or failed; the customer types the data by hand and the step advances. */
	FAILED

}
