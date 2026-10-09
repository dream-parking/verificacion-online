package com.dreamparking.backend.identity.ocr;

import java.util.Base64;

/** A photo of one side of the identity document, as uploaded (JPEG or PNG). */
public final class DocumentPhoto {

	private final byte[] content;

	private final String contentType;

	public DocumentPhoto(byte[] content, String contentType) {
		this.content = content.clone();
		this.contentType = contentType;
	}

	public byte[] content() {
		return content.clone();
	}

	public String contentType() {
		return contentType;
	}

	public int size() {
		return content.length;
	}

	/** {@code data:} URL, the way the provider receives inline images. */
	String dataUrl() {
		return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(content);
	}

}
