package com.dreamparking.backend.identity.ocr;

/** Reads the data printed on a Salvadoran DUI from photos of its front and back. */
public interface DuiOcrClient {

	/**
	 * @return what could be read; an illegible photo is a normal result ({@code readable == false}), not an error
	 * @throws DuiOcrException when the provider is off, fails or times out
	 */
	DuiReading read(DocumentPhoto front, DocumentPhoto back) throws DuiOcrException;

	/** Name of the model or provider, stored with each reading. */
	String model();

}
