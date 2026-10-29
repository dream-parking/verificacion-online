package com.dreamparking.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.dreamparking.backend.console.entity.enums.ConsoleRole;
import com.jayway.jsonpath.JsonPath;

/**
 * VDI-148: an approved request registers the product it chooses by sending only the product code; everything else is
 * taken from the request. It chooses once, and only a product it qualifies for. The errors and the timeline event are
 * in Spanish.
 */
@Import({ TestcontainersConfiguration.class, TestAuth.class })
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ProductRequestApiTests {

	private static final String PRODUCT_REQUEST = "/api/onboarding/product-request";

	@Autowired
	WebApplicationContext context;

	@Autowired
	TestAuth auth;

	@Autowired
	JdbcTemplate jdbc;

	MockMvc app;

	@BeforeEach
	void signIn() {
		app = auth.mockMvcAs(context, auth.user(ConsoleRole.ADMIN));
	}

	@Test
	void anApprovedRequestRegistersItsProductWithoutAskingForAnythingElse() throws Exception {
		String requestId = approved();

		choose(requestId, "AHORRO_PLUS").andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.requestId").value(requestId))
			.andExpect(jsonPath("$.requestNumber").value(containsString("SOL-")))
			.andExpect(jsonPath("$.product.code").value("AHORRO_PLUS"))
			.andExpect(jsonPath("$.product.name").value("Cuenta de ahorro plus"))
			.andExpect(jsonPath("$.product.minimumOpeningDepositUsd").value(100.00))
			.andExpect(jsonPath("$.requestedAt").isNotEmpty());

		assertThat(jdbc.queryForObject("""
				select p.code from product_request r join product p on p.id = r.product_id
				where r.request_id = ?::uuid""", String.class, requestId))
			.isEqualTo("AHORRO_PLUS");
		perform(get("/api/console/requests/{id}", requestId))
			.andExpect(jsonPath("$.timeline[?(@.type == 'PRODUCT_REQUESTED')].description")
				.value(hasItem("Producto elegido: Cuenta de ahorro plus")));
	}

	@Test
	void aRequestChoosesOnlyOnceTheSameProductOrAnother() throws Exception {
		String requestId = approved();
		choose(requestId, "AHORRO_BASICA").andExpect(status().isCreated());

		choose(requestId, "AHORRO_BASICA").andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail")
				.value("La solicitud ya eligió un producto: Cuenta de ahorro básica. Solo se elige una vez"));
		choose(requestId, "CORRIENTE").andExpect(status().isConflict());
		assertThat(jdbc.queryForObject("select count(*) from product_request where request_id = ?::uuid",
				Integer.class, requestId))
			.isEqualTo(1);
	}

	@Test
	void aProductTheRequestDoesNotQualifyForIsRefused() throws Exception {
		String requestId = approved();
		jdbc.update("update product set active = false where code = 'CORRIENTE'");

		choose(requestId, "CORRIENTE").andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("La solicitud no califica para el producto Cuenta corriente"));
	}

	@Test
	void aRequestThatIsNotApprovedCannotChoose() throws Exception {
		String requestId = idOf(perform(post("/api/onboarding/requests")).andExpect(status().isCreated()));

		choose(requestId, "AHORRO_BASICA").andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(containsString("Only submitted and approved requests")));
	}

	@Test
	void anUnknownRequestOrProductIsNotFound() throws Exception {
		UUID unknown = UUID.randomUUID();
		choose(unknown.toString(), "AHORRO_BASICA").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("No existe la solicitud " + unknown));

		choose(approved(), "PLAZO_FIJO").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("No existe el producto PLAZO_FIJO"));
	}

	@Test
	void theRequestAndAWellFormedProductCodeAreRequired() throws Exception {
		String requestId = approved();

		perform(post(PRODUCT_REQUEST).content("""
				{"productCode": "AHORRO_BASICA"}
				""")).andExpect(status().isBadRequest());
		perform(post(PRODUCT_REQUEST).content("""
				{"requestId": "%s"}
				""".formatted(requestId))).andExpect(status().isBadRequest());
		perform(post(PRODUCT_REQUEST).content("""
				{"requestId": "no-es-uuid", "productCode": "AHORRO_BASICA"}
				""")).andExpect(status().isBadRequest());
		choose(requestId, "ahorro_basica").andExpect(status().isBadRequest());
		choose(requestId, "A".repeat(31)).andExpect(status().isBadRequest());
	}

	@Test
	void theChoiceCannotBeChanged() throws Exception {
		String requestId = approved();
		choose(requestId, "AHORRO_BASICA").andExpect(status().isCreated());

		assertThatThrownBy(() -> jdbc.update("""
				update product_request set product_id = (select id from product where code = 'CORRIENTE')
				where request_id = ?::uuid""", requestId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be modified or deleted");
	}

	@Test
	void theChoiceCannotBeDeleted() throws Exception {
		String requestId = approved();
		choose(requestId, "AHORRO_BASICA").andExpect(status().isCreated());

		assertThatThrownBy(() -> jdbc.update("delete from product_request where request_id = ?::uuid", requestId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be modified or deleted");
	}

	@Test
	void theDatabaseKeepsOneChoicePerRequest() throws Exception {
		String requestId = approved();
		choose(requestId, "AHORRO_BASICA").andExpect(status().isCreated());

		assertThatThrownBy(() -> jdbc.update("""
				insert into product_request (request_id, product_id)
				values (?::uuid, (select id from product where code = 'CORRIENTE'))""", requestId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("uq_product_request_request");
	}

	@Test
	void deletingTheRequestStillCascadesToItsChoice() throws Exception {
		String requestId = approved();
		choose(requestId, "AHORRO_BASICA").andExpect(status().isCreated());

		jdbc.update("delete from onboarding_request where id = ?::uuid", requestId);

		assertThat(jdbc.queryForObject("select count(*) from product_request where request_id = ?::uuid",
				Integer.class, requestId))
			.isZero();
	}

	// ---------------------------------------------------------------- helpers

	private ResultActions choose(String requestId, String productCode) throws Exception {
		return perform(post(PRODUCT_REQUEST).content("""
				{"requestId": "%s", "productCode": "%s"}
				""".formatted(requestId, productCode)));
	}

	/** The whole mobile flow up to an approved submission. */
	private String approved() throws Exception {
		String requestId = idOf(perform(post("/api/onboarding/requests")).andExpect(status().isCreated()));
		perform(put("/api/onboarding/requests/{id}/privacy-consent", requestId).content("""
				{"signalsAccepted": true}
				""")).andExpect(status().isOk());
		perform(put("/api/onboarding/requests/{id}/basic-data", requestId).content("""
				{"dui": "01234567-8", "firstNames": "Ana Sofía", "lastNames": "Pérez López", "mobilePhone": "7123-4567"}
				""")).andExpect(status().isOk());
		DuiStep.complete(app, requestId, "01234567-8", "Ana Sofía", "Pérez López");
		perform(put("/api/onboarding/requests/{id}/income", requestId).content("""
				{"sourceCode": "SALARIO", "rangeCode": "HASTA_500"}
				""")).andExpect(status().isNoContent());
		perform(put("/api/onboarding/requests/{id}/expected-activity", requestId).content("""
				{"transactionTypeCode": "PAGO_SALARIO", "monthlyAmountRangeCode": "200_500"}
				""")).andExpect(status().isOk());
		perform(post("/api/onboarding/requests/{id}/submit", requestId)).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"));
		return requestId;
	}

	private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		return app.perform(request.contentType(MediaType.APPLICATION_JSON));
	}

	private static String idOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}

}
