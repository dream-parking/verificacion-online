package com.dreamparking.backend.identity.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Encrypts the identity document photos with AES-256-GCM before they reach the database (VDI-80). Each photo gets
 * a random 12-byte IV, and the caller's associated data (request and side) is authenticated with it, so a
 * ciphertext moved to another row does not decrypt.
 * <p>
 * The key is {@code app.documents.encryption-key} (base64 of 32 bytes, a Container App secret). Without it the
 * application does not start, except with the {@code dev} profile, which uses a random key and warns: the photos
 * saved then cannot be read after a restart.
 */
@Component
public class DocumentCipher {

	private static final Logger log = LoggerFactory.getLogger(DocumentCipher.class);

	static final int IV_BYTES = 12;

	static final int TAG_BITS = 128;

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";

	private final SecureRandom random = new SecureRandom();

	private final SecretKey key;

	private final short keyVersion;

	public DocumentCipher(@Value("${app.documents.encryption-key:}") String encodedKey,
			@Value("${app.documents.key-version:1}") short keyVersion, Environment environment) {
		this.keyVersion = keyVersion;
		this.key = encodedKey.isBlank() ? temporaryKey(environment) : decode(encodedKey);
	}

	/** Ciphertext (with the GCM tag at the end) and the IV it was encrypted with. */
	public record Sealed(byte[] iv, byte[] ciphertext, short keyVersion) {
	}

	public Sealed encrypt(byte[] plain, String associatedData) {
		byte[] iv = new byte[IV_BYTES];
		random.nextBytes(iv);
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
			return new Sealed(iv, cipher.doFinal(plain), keyVersion);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Cannot encrypt the document image", ex);
		}
	}

	/** @throws IllegalStateException when the key or the associated data do not match (tampered or misplaced row) */
	public byte[] decrypt(byte[] iv, byte[] ciphertext, short version, String associatedData) {
		if (version != keyVersion) {
			throw new IllegalStateException("No key for document key version " + version);
		}
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
			return cipher.doFinal(ciphertext);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Cannot decrypt the document image", ex);
		}
	}

	private static SecretKey decode(String encodedKey) {
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(encodedKey.trim());
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException("app.documents.encryption-key must be base64", ex);
		}
		if (bytes.length != 32) {
			throw new IllegalStateException("app.documents.encryption-key must be 32 bytes (AES-256), it has " + bytes.length);
		}
		return new SecretKeySpec(bytes, "AES");
	}

	private static SecretKey temporaryKey(Environment environment) {
		if (!environment.matchesProfiles("dev")) {
			throw new IllegalStateException("app.documents.encryption-key (APP_DOCUMENT_KEY) is required");
		}
		log.warn("APP_DOCUMENT_KEY is not set: identity document photos are encrypted with a temporary key and "
				+ "cannot be read after a restart");
		try {
			KeyGenerator generator = KeyGenerator.getInstance("AES");
			generator.init(256);
			return generator.generateKey();
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Cannot generate a temporary document key", ex);
		}
	}

}
