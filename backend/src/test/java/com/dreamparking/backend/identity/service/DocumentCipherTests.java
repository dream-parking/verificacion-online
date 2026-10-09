package com.dreamparking.backend.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** VDI-80: AES-256-GCM of the DUI photos. */
class DocumentCipherTests {

	static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

	static final byte[] PHOTO = { (byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2, 3 };

	final DocumentCipher cipher = new DocumentCipher(KEY, (short) 1, new MockEnvironment());

	@Test
	void decryptsWhatItEncrypted() {
		DocumentCipher.Sealed sealed = cipher.encrypt(PHOTO, "request:FRONT");

		assertThat(sealed.iv()).hasSize(DocumentCipher.IV_BYTES);
		assertThat(sealed.ciphertext()).hasSize(PHOTO.length + DocumentCipher.TAG_BITS / 8).isNotEqualTo(PHOTO);
		assertThat(sealed.keyVersion()).isEqualTo((short) 1);
		assertThat(cipher.decrypt(sealed.iv(), sealed.ciphertext(), (short) 1, "request:FRONT")).isEqualTo(PHOTO);
	}

	@Test
	void everyPhotoGetsItsOwnIv() {
		assertThat(cipher.encrypt(PHOTO, "a").iv()).isNotEqualTo(cipher.encrypt(PHOTO, "a").iv());
	}

	@Test
	void aCiphertextMovedToAnotherRowDoesNotDecrypt() {
		DocumentCipher.Sealed sealed = cipher.encrypt(PHOTO, "request:FRONT");

		assertThatThrownBy(() -> cipher.decrypt(sealed.iv(), sealed.ciphertext(), (short) 1, "request:BACK"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Cannot decrypt the document image");
	}

	@Test
	void anotherKeyCannotDecrypt() {
		DocumentCipher.Sealed sealed = cipher.encrypt(PHOTO, "x");
		byte[] other = new byte[32];
		other[0] = 1;
		DocumentCipher otherCipher = new DocumentCipher(Base64.getEncoder().encodeToString(other), (short) 1,
				new MockEnvironment());

		assertThatThrownBy(() -> otherCipher.decrypt(sealed.iv(), sealed.ciphertext(), (short) 1, "x"))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> cipher.decrypt(sealed.iv(), sealed.ciphertext(), (short) 2, "x"))
			.hasMessage("No key for document key version 2");
	}

	@Test
	void theKeyMustBe32BytesOfBase64() {
		assertThatThrownBy(() -> new DocumentCipher("not base64!", (short) 1, new MockEnvironment()))
			.hasMessage("app.documents.encryption-key must be base64");
		assertThatThrownBy(() -> new DocumentCipher(Base64.getEncoder().encodeToString(new byte[16]), (short) 1,
				new MockEnvironment()))
			.hasMessage("app.documents.encryption-key must be 32 bytes (AES-256), it has 16");
	}

	@Test
	void withoutAKeyOnlyTheDevProfileStarts() {
		assertThatThrownBy(() -> new DocumentCipher("", (short) 1, new MockEnvironment()))
			.hasMessage("app.documents.encryption-key (APP_DOCUMENT_KEY) is required");

		MockEnvironment dev = new MockEnvironment();
		dev.setActiveProfiles("dev");
		DocumentCipher temporary = new DocumentCipher("", (short) 1, dev);
		DocumentCipher.Sealed sealed = temporary.encrypt(PHOTO, "x");
		assertThat(temporary.decrypt(sealed.iv(), sealed.ciphertext(), (short) 1, "x")).isEqualTo(PHOTO);
	}

}
