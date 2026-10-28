package com.dreamparking.backend.signature.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The account contract in force (VDI-130): its version, its text and the SHA-256 fingerprint of that text. The backend
 * owns the text, so the fingerprint stored with a signature always proves which exact words were signed; the app only
 * says which version it showed. The text lives in {@code contracts/contrato-apertura-<version>.txt}; publishing a new
 * version is a new file and a new {@code app.contract.version}.
 */
@Component
public class ContractTerms {

	private final String version;

	private final String text;

	private final String fingerprint;

	public ContractTerms(@Value("${app.contract.version:2026.1}") String version) {
		this.version = version.trim();
		this.text = load(this.version);
		this.fingerprint = fingerprintOf(this.text);
	}

	public String version() {
		return version;
	}

	public String text() {
		return text;
	}

	/** SHA-256 of the text in UTF-8, as 64 lower-case hex characters. */
	public String fingerprint() {
		return fingerprint;
	}

	/**
	 * Reads the text with {@code \n} line ends: git may check the file out with {@code \r\n} on Windows, and the
	 * fingerprint must not depend on the machine that runs the backend.
	 */
	private static String load(String version) {
		ClassPathResource resource = new ClassPathResource("contracts/contrato-apertura-" + version + ".txt");
		try (InputStream in = resource.getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
		}
		catch (IOException ex) {
			throw new IllegalStateException("The text of contract version " + version + " was not found", ex);
		}
	}

	static String fingerprintOf(String text) {
		try {
			MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(sha256.digest(text.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}

}
