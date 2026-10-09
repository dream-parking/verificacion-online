package com.dreamparking.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.dreamparking.backend.console.entity.ConsoleUser;
import com.dreamparking.backend.console.entity.enums.ConsoleRole;
import com.dreamparking.backend.identity.entity.enums.UnreadableReason;
import com.dreamparking.backend.identity.entity.enums.UnreadableSide;
import com.dreamparking.backend.identity.ocr.DuiOcrClient;
import com.dreamparking.backend.identity.ocr.DuiOcrException;
import com.dreamparking.backend.identity.ocr.DuiReading;
import com.jayway.jsonpath.JsonPath;

/**
 * VDI-79 / VDI-80, flow of the Sprint 2 prototype: basic data → DUI photos (read by the OCR provider) → the customer
 * confirms or corrects the data read → income. The photos are kept encrypted in the file. The provider is mocked; its
 * client has its own tests.
 */
@Import({ TestcontainersConfiguration.class, TestAuth.class })
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = "app.onboarding.identity-document-required=true")
@Transactional
class IdentityDocumentApiTests {

	/** Smallest JPEG and PNG headers: the server only checks the first bytes. */
	static final byte[] FRONT = { (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 1, 2, 3, 4, 5 };

	static final byte[] BACK = { (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 9, 8, 7 };

	static final String BASIC_DATA = """
			{"dui": "04567891-2", "firstNames": "Marta Alejandra", "lastNames": "Rivas Cruz", "mobilePhone": "7123-4567"}
			""";

	/** What the customer confirms: accents kept (not a correction) and the birth date corrected. */
	static final String CONFIRMATION = """
			{"dui": "04567891-2", "firstNames": "Marta Alejandra", "lastNames": "Rivas Cruz",
			 "birthDate": "1991-03-14", "expiryDate": "2031-07-22"}
			""";

	@Autowired
	WebApplicationContext context;

	@Autowired
	TestAuth auth;

	@Autowired
	EntityManager em;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean
	DuiOcrClient ocr;

	MockMvc mvc;

	ConsoleUser analyst;

	@BeforeEach
	void signIn() {
		analyst = auth.user(ConsoleRole.FRAUD_ANALYST);
		mvc = auth.mockMvcAs(context, analyst);
		when(ocr.model()).thenReturn("gpt-6-astra");
	}

	static DuiReading marta() {
		return new DuiReading(true, null, null, "04567891-2", "Marta Alejandra", "Rivas Cruz", LocalDate.of(1991, 8, 14),
				LocalDate.of(2021, 7, 22), LocalDate.of(2031, 7, 22), "F", true, new BigDecimal("0.97"));
	}

	@Test
	void readsTheDuiAndTheConsoleSeesWhatTheCustomerConfirmedAndCorrected() throws Exception {
		when(ocr.read(any(), any())).thenReturn(marta());
		String id = withBasicData();

		upload(id, FRONT, BACK).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("READ"))
			.andExpect(jsonPath("$.attempts").value(1))
			.andExpect(jsonPath("$.dui").value("04567891-2"))
			.andExpect(jsonPath("$.firstNames").value("Marta Alejandra"))
			.andExpect(jsonPath("$.lastNames").value("Rivas Cruz"))
			.andExpect(jsonPath("$.birthDate").value("1991-08-14"))
			.andExpect(jsonPath("$.expiryDate").value("2031-07-22"))
			.andExpect(jsonPath("$.gender").value("F"))
			// fraud signals stay in the console
			.andExpect(jsonPath("$.looksAuthentic").doesNotExist())
			.andExpect(jsonPath("$.confidence").doesNotExist());
		// The photos alone do not complete the step: the customer confirms first.
		mvc.perform(get("/api/onboarding/requests/{id}", id)).andExpect(jsonPath("$.completedSteps").value(2));

		confirm(id, CONFIRMATION).andExpect(status().isOk()).andExpect(jsonPath("$.completedSteps").value(3));

		flushAndClear();
		mvc.perform(get("/api/console/requests/{id}", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.identityDocument.ocrStatus").value("READ"))
			.andExpect(jsonPath("$.identityDocument.ocrModel").value("gpt-6-astra"))
			.andExpect(jsonPath("$.identityDocument.read.birthDate").value("1991-08-14"))
			.andExpect(jsonPath("$.identityDocument.confirmed.birthDate").value("1991-03-14"))
			.andExpect(jsonPath("$.identityDocument.confirmed.firstNames").value("Marta Alejandra"))
			.andExpect(jsonPath("$.identityDocument.confirmedAt").exists())
			.andExpect(jsonPath("$.identityDocument.correctedFields").value(contains("birthDate")))
			.andExpect(jsonPath("$.identityDocument.duiMatchesDeclared").value(true))
			.andExpect(jsonPath("$.identityDocument.namesMatchDeclared").value(true))
			.andExpect(jsonPath("$.identityDocument.checkDigitValid").value(false))
			.andExpect(jsonPath("$.identityDocument.expired").value(false))
			.andExpect(jsonPath("$.identityDocument.looksAuthentic").value(true))
			.andExpect(jsonPath("$.identityDocument.confidence").value(0.97))
			.andExpect(jsonPath("$.identityDocument.photos").value(contains("BACK", "FRONT")))
			.andExpect(jsonPath("$.timeline[*].type").value(hasItem("IDENTITY_DOCUMENT_CAPTURED")))
			.andExpect(jsonPath("$.timeline[*].type").value(hasItem("IDENTITY_DOCUMENT_CONFIRMED")));
	}

	@Test
	void aDuiDifferentFromTheBasicDataIsASignalNotAnError() throws Exception {
		when(ocr.read(any(), any())).thenReturn(marta());
		String id = withBasicData();
		upload(id, FRONT, BACK).andExpect(status().isOk());

		confirm(id, """
				{"dui": "04567891-3", "firstNames": "Marta", "lastNames": "Rivas Cruz",
				 "birthDate": "1991-08-14", "expiryDate": "2031-07-22"}
				""").andExpect(status().isOk());
		flushAndClear();
		mvc.perform(get("/api/console/requests/{id}", id))
			.andExpect(jsonPath("$.identityDocument.correctedFields").value(contains("dui", "firstNames")))
			.andExpect(jsonPath("$.identityDocument.duiMatchesDeclared").value(false))
			.andExpect(jsonPath("$.identityDocument.namesMatchDeclared").value(false));

		// Fixing the basic data afterwards compares again.
		perform(put("/api/onboarding/requests/{id}/basic-data", id).content("""
				{"dui": "04567891-3", "firstNames": "Marta", "lastNames": "Rivas Cruz", "mobilePhone": "7123-4568"}
				""")).andExpect(status().isOk());
		flushAndClear();
		mvc.perform(get("/api/console/requests/{id}", id))
			.andExpect(jsonPath("$.identityDocument.duiMatchesDeclared").value(true))
			.andExpect(jsonPath("$.identityDocument.namesMatchDeclared").value(true));
	}

	@Test
	void theConsoleDownloadsTheDecryptedPhotoAndTheAccessIsAudited() throws Exception {
		when(ocr.read(any(), any())).thenReturn(marta());
		String id = withBasicData();
		upload(id, FRONT, BACK).andExpect(status().isOk());
		flushAndClear();

		mvc.perform(get("/api/console/requests/{id}/identity-document/FRONT", id))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.IMAGE_JPEG))
			.andExpect(content().bytes(FRONT))
			.andExpect(header().string("Cache-Control", "no-store"));
		mvc.perform(get("/api/console/requests/{id}/identity-document/BACK", id))
			.andExpect(content().contentType(MediaType.IMAGE_PNG))
			.andExpect(content().bytes(BACK));
		mvc.perform(get("/api/console/requests/{id}/identity-document/FRONT", UUID.randomUUID()))
			.andExpect(status().isNotFound());

		Integer audits = jdbc.queryForObject(
				"select count(*) from access_audit where user_id = ? and action = 'VIEW_IDENTITY_DOCUMENT' and entity_id = ?",
				Integer.class, analyst.getId(), id + "/FRONT");
		assertThat(audits).isEqualTo(1);
	}

	@Test
	void thePhotosAreStoredEncrypted() throws Exception {
		when(ocr.read(any(), any())).thenReturn(marta());
		String id = withBasicData();
		upload(id, FRONT, BACK).andExpect(status().isOk());
		flushAndClear();

		byte[] stored = jdbc.queryForObject(
				"select ciphertext from identity_document_image where request_id = ?::uuid and side = 'FRONT'",
				byte[].class, id);
		assertThat(stored).hasSize(FRONT.length + 16); // GCM tag
		assertThat(Arrays.equals(Arrays.copyOf(stored, FRONT.length), FRONT)).isFalse();
		assertThat(jdbc.queryForObject(
				"select sha256 from identity_document_image where request_id = ?::uuid and side = 'FRONT'", String.class,
				id)).hasSize(64);
	}

	@Test
	void unreadablePhotosSayWhichSideToTakeAgain() throws Exception {
		when(ocr.read(any(), any())).thenReturn(new DuiReading(false, UnreadableReason.GLARE, UnreadableSide.FRONT, null,
				null, null, null, null, null, null, true, null));
		String id = withBasicData();

		upload(id, FRONT, BACK).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.reason").value("GLARE"))
			.andExpect(jsonPath("$.side").value("FRONT"))
			.andExpect(jsonPath("$.detail").value("The identity document photos cannot be read: GLARE"));
		// Nothing to confirm yet.
		confirm(id, CONFIRMATION).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Capture readable photos of the identity document first"));

		// The attempt is on file for the console even though the request answered an error.
		flushAndClear();
		mvc.perform(get("/api/console/requests/{id}", id))
			.andExpect(jsonPath("$.identityDocument.ocrStatus").value("UNREADABLE"))
			.andExpect(jsonPath("$.identityDocument.unreadableReason").value("GLARE"))
			.andExpect(jsonPath("$.identityDocument.unreadableSide").value("FRONT"))
			.andExpect(jsonPath("$.identityDocument.read").doesNotExist());

		when(ocr.read(any(), any())).thenReturn(marta());
		upload(id, FRONT, BACK).andExpect(status().isOk()).andExpect(jsonPath("$.attempts").value(2));
		confirm(id, CONFIRMATION).andExpect(jsonPath("$.completedSteps").value(3));
	}

	@Test
	void aReadingWithoutTheDuiNumberIsUnreadable() throws Exception {
		when(ocr.read(any(), any())).thenReturn(
				new DuiReading(true, null, null, null, "Marta", "Rivas", null, null, null, null, true, null));
		String id = withBasicData();

		upload(id, FRONT, BACK).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.reason").value("OTHER"))
			.andExpect(jsonPath("$.side").doesNotExist());
	}

	@Test
	void aProviderFailureLetsTheCustomerTypeTheData() throws Exception {
		when(ocr.read(any(), any())).thenThrow(new DuiOcrException("provider error: ResourceAccessException"));
		String id = withBasicData();

		upload(id, FRONT, BACK).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("FAILED"))
			.andExpect(jsonPath("$.dui").doesNotExist());
		confirm(id, CONFIRMATION).andExpect(jsonPath("$.completedSteps").value(3));

		flushAndClear();
		mvc.perform(get("/api/console/requests/{id}", id))
			.andExpect(jsonPath("$.identityDocument.ocrStatus").value("FAILED"))
			.andExpect(jsonPath("$.identityDocument.failure").value("provider error: ResourceAccessException"))
			.andExpect(jsonPath("$.identityDocument.read").doesNotExist())
			.andExpect(jsonPath("$.identityDocument.confirmed.dui").value("04567891-2"))
			// typed by hand: nothing was read, so nothing counts as corrected
			.andExpect(jsonPath("$.identityDocument.correctedFields").isEmpty())
			.andExpect(jsonPath("$.identityDocument.duiMatchesDeclared").value(true))
			.andExpect(jsonPath("$.identityDocument.photos").value(contains("BACK", "FRONT")));
	}

	@Test
	void theDuiStepComesAfterTheBasicData() throws Exception {
		String id = startAndAccept();

		upload(id, FRONT, BACK).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Register the basic data before the identity document"));
		confirm(id, CONFIRMATION).andExpect(status().isConflict());
		verify(ocr, never()).read(any(), any());
	}

	@Test
	void incomeDoesNotSkipTheRequiredDuiStep() throws Exception {
		String id = withBasicData();

		perform(put("/api/onboarding/requests/{id}/income", id).content("""
				{"sourceCode": "SALARIO", "rangeCode": "HASTA_500"}
				""")).andExpect(status().isNoContent());
		mvc.perform(get("/api/onboarding/requests/{id}", id)).andExpect(jsonPath("$.completedSteps").value(2));
	}

	@Test
	void rejectsInvalidConfirmations() throws Exception {
		when(ocr.read(any(), any())).thenReturn(marta());
		String id = withBasicData();
		upload(id, FRONT, BACK).andExpect(status().isOk());

		confirm(id, """
				{"dui": "045678912", "firstNames": "Marta 2", "lastNames": "", "birthDate": "2999-01-01"}
				""").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.dui").exists())
			.andExpect(jsonPath("$.errors.firstNames").exists())
			.andExpect(jsonPath("$.errors.lastNames").exists())
			.andExpect(jsonPath("$.errors.birthDate").exists())
			.andExpect(jsonPath("$.errors.expiryDate").exists());
	}

	@Test
	void rejectsFilesThatAreNotJpegOrPngWithoutCallingTheProvider() throws Exception {
		String id = withBasicData();

		upload(id, "%PDF-1.7".getBytes(), BACK).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("The front photo must be a JPEG or PNG image"));
		upload(id, FRONT, new byte[0]).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("The back photo is empty"));
		mvc.perform(multipart(HttpMethod.PUT, "/api/onboarding/requests/{id}/identity-document", id)
			.file(new MockMultipartFile("front", "front.jpg", "image/jpeg", FRONT)))
			.andExpect(status().isBadRequest());
		verify(ocr, never()).read(any(), any());
	}

	@Test
	void unknownRequestIsNotFound() throws Exception {
		upload(UUID.randomUUID().toString(), FRONT, BACK).andExpect(status().isNotFound());
	}

	@Test
	void newPhotosAfterConfirmingHaveToBeConfirmedAgain() throws Exception {
		when(ocr.read(any(), any())).thenReturn(marta());
		String id = withBasicData();
		upload(id, FRONT, BACK).andExpect(status().isOk());
		confirm(id, CONFIRMATION).andExpect(status().isOk());
		declareIncomeAndActivity(id);

		upload(id, FRONT, BACK).andExpect(status().isOk());
		perform(post("/api/onboarding/requests/{id}/submit", id)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Confirm the data of the identity document before submitting"));

		confirm(id, CONFIRMATION).andExpect(status().isOk());
		perform(post("/api/onboarding/requests/{id}/submit", id)).andExpect(status().isOk())
			.andExpect(jsonPath("$.completedSteps").value(6));
	}

	@Test
	void afterSubmittingTheDocumentCannotBeReplacedOrChanged() throws Exception {
		String id = submittedWithDui();

		upload(id, FRONT, BACK).andExpect(status().isConflict());
		flushAndClear();
		assertThatThrownBy(() -> jdbc.update("update identity_document set dui = '99999999-9' where request_id = ?::uuid", id))
			.hasMessageContaining("cannot be modified or deleted");
	}

	@Test
	void afterSubmittingThePhotosCannotBeDeleted() throws Exception {
		String id = submittedWithDui();

		assertThatThrownBy(() -> jdbc.update("delete from identity_document_image where request_id = ?::uuid", id))
			.hasMessageContaining("cannot be modified or deleted");
	}

	private String submittedWithDui() throws Exception {
		when(ocr.read(any(), any())).thenReturn(marta());
		String id = withBasicData();
		upload(id, FRONT, BACK).andExpect(status().isOk());
		confirm(id, CONFIRMATION).andExpect(status().isOk());
		declareIncomeAndActivity(id);
		perform(post("/api/onboarding/requests/{id}/submit", id)).andExpect(status().isOk())
			.andExpect(jsonPath("$.completedSteps").value(6));
		flushAndClear();
		return id;
	}

	private void declareIncomeAndActivity(String id) throws Exception {
		perform(put("/api/onboarding/requests/{id}/income", id).content("""
				{"sourceCode": "SALARIO", "rangeCode": "HASTA_500"}
				""")).andExpect(status().isNoContent());
		perform(put("/api/onboarding/requests/{id}/expected-activity", id).content("""
				{"transactionTypeCode": "PAGO_SALARIO", "monthlyAmountRangeCode": "200_500"}
				""")).andExpect(status().isOk());
	}

	private String startAndAccept() throws Exception {
		String id = JsonPath.read(perform(post("/api/onboarding/requests")).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.id");
		perform(put("/api/onboarding/requests/{id}/privacy-consent", id).content("{\"signalsAccepted\": true}"))
			.andExpect(jsonPath("$.completedSteps").value(1));
		return id;
	}

	private String withBasicData() throws Exception {
		String id = startAndAccept();
		perform(put("/api/onboarding/requests/{id}/basic-data", id).content(BASIC_DATA))
			.andExpect(jsonPath("$.completedSteps").value(2));
		return id;
	}

	private ResultActions upload(String id, byte[] front, byte[] back) throws Exception {
		return mvc.perform(multipart(HttpMethod.PUT, "/api/onboarding/requests/{id}/identity-document", id)
			.file(new MockMultipartFile("front", "front.jpg", "image/jpeg", front))
			.file(new MockMultipartFile("back", "back.png", "image/png", back)));
	}

	private ResultActions confirm(String id, String body) throws Exception {
		return perform(put("/api/onboarding/requests/{id}/identity-document/confirmation", id).content(body));
	}

	private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.contentType(MediaType.APPLICATION_JSON));
	}

	private void flushAndClear() {
		em.flush();
		em.clear();
	}

}
