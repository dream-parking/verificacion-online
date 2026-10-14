package com.dreamparking.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The DUI step of the onboarding flow, which is required: the photos and the confirmation. In tests the reader is
 * off, so the reading is {@code FAILED} and the customer types the data.
 */
final class DuiStep {

	/** Smallest JPEG header: the server only checks the first bytes. */
	private static final byte[] PHOTO = { (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 1, 2, 3 };

	private DuiStep() {
	}

	/** Captures and confirms the DUI declared in the basic data, valid until 2031. */
	static void complete(MockMvc mvc, String requestId, String dui, String firstNames, String lastNames)
			throws Exception {
		mvc.perform(multipart(HttpMethod.PUT, "/api/onboarding/requests/{id}/identity-document", requestId)
			.file(new MockMultipartFile("front", "front.jpg", MediaType.IMAGE_JPEG_VALUE, PHOTO))
			.file(new MockMultipartFile("back", "back.jpg", MediaType.IMAGE_JPEG_VALUE, PHOTO)))
			.andExpect(status().isOk());
		mvc.perform(put("/api/onboarding/requests/{id}/identity-document/confirmation", requestId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"dui": "%s", "firstNames": "%s", "lastNames": "%s", "birthDate": "1991-03-14", "expiryDate": "2031-07-22"}
					""".formatted(dui, firstNames, lastNames)))
			.andExpect(status().isOk());
	}

}
