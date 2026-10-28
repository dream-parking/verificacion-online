package com.dreamparking.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.dreamparking.backend.console.entity.ConsoleUser;
import com.dreamparking.backend.console.entity.enums.ConsoleRole;
import com.dreamparking.backend.customer.entity.Customer;
import com.dreamparking.backend.customer.entity.Device;
import com.dreamparking.backend.customer.repository.CustomerRepository;
import com.dreamparking.backend.customer.repository.DeviceRepository;
import com.dreamparking.backend.risklist.dto.RiskListMatch;
import com.dreamparking.backend.risklist.entity.enums.RiskListDataType;
import com.dreamparking.backend.risklist.entity.enums.RiskListType;
import com.dreamparking.backend.risklist.service.RiskListLookupService;
import com.jayway.jsonpath.JsonPath;

/**
 * VDI-142: an administrator puts a DUI, a phone or a device on an internal risk list and takes it off; the lookup finds
 * what is active, whatever the name of the person. The values are stored only as hashes and are never returned.
 */
@Import({ TestcontainersConfiguration.class, TestAuth.class })
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class RiskListApiTests {

	static final String LISTS = "/api/console/admin/risk-lists";

	static final String FRAUD = LISTS + "/FRAUDE_CONFIRMADO/entries";

	static final String WATCH = LISTS + "/VIGILANCIA/entries";

	@Autowired
	WebApplicationContext context;

	@Autowired
	TestAuth auth;

	@Autowired
	RiskListLookupService lookup;

	@Autowired
	CustomerRepository customers;

	@Autowired
	DeviceRepository devices;

	@Autowired
	JdbcTemplate jdbc;

	ConsoleUser admin;

	MockMvc mvc;

	@BeforeEach
	void signIn() {
		admin = auth.user(ConsoleRole.ADMIN);
		mvc = auth.mockMvcAs(context, admin);
	}

	@Test
	void theSeededListsHaveATypeAndAWeight() throws Exception {
		mvc.perform(get(LISTS))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(3))
			.andExpect(jsonPath("$[0].code").value("FRAUDE_CONFIRMADO"))
			.andExpect(jsonPath("$[0].type").value("CONFIRMED_FRAUD"))
			.andExpect(jsonPath("$[0].weight").value(100))
			.andExpect(jsonPath("$[1].code").value("EN_INVESTIGACION"))
			.andExpect(jsonPath("$[2].weight").value(30));
	}

	@Test
	void aDocumentOnTheListIsFoundWhateverTheNameOfThePerson() throws Exception {
		// A customer as a real request stores it, with a name unrelated to whoever was reported.
		Customer customer = customer("04567891-2", "7845-2310", "Otra", "Persona Distinta");
		assertThat(lookup.findMatches(customer.getDui(), null, null)).isEmpty();

		add(FRAUD, "DOCUMENT", "045678912", "Fraude confirmado en el caso 114");

		List<RiskListMatch> matches = lookup.findMatches(customer.getDui(), null, null);
		assertThat(matches).singleElement().satisfies(match -> {
			assertThat(match.listCode()).isEqualTo("FRAUDE_CONFIRMADO");
			assertThat(match.listName()).isEqualTo("Fraude confirmado");
			assertThat(match.listType()).isEqualTo(RiskListType.CONFIRMED_FRAUD);
			assertThat(match.weight()).isEqualTo(100);
			assertThat(match.dataType()).isEqualTo(RiskListDataType.DOCUMENT);
		});
	}

	@Test
	void aPhoneOnTheListIsFoundWhateverTheNameOfThePerson() throws Exception {
		Customer customer = customer("01234567-8", "7845-2310", "Otra", "Persona Distinta");
		add(FRAUD, "PHONE", "+503 7845 2310", "Teléfono de la red de fraude");

		assertThat(lookup.findMatches(null, customer.getMobilePhone(), null)).singleElement()
			.satisfies(match -> assertThat(match.dataType()).isEqualTo(RiskListDataType.PHONE));
	}

	@Test
	void aDeviceOnTheListIsFoundWhateverTheNameAndTheDocument() throws Exception {
		Device device = new Device();
		device.setFingerprint("a9c3·77e1·0b5d");
		devices.saveAndFlush(device);
		add(FRAUD, "DEVICE", " a9c3·77e1·0b5d ", "Dispositivo usado en varios fraudes");

		// the customer that comes back has another name and another DUI: only the device is on the list
		Customer returning = customer("09876543-2", "6123-4567", "Nombre", "Nuevo");
		List<RiskListMatch> matches = lookup.findMatches(returning.getDui(), returning.getMobilePhone(),
				device.getFingerprint());

		assertThat(matches).singleElement()
			.satisfies(match -> assertThat(match.dataType()).isEqualTo(RiskListDataType.DEVICE));
		assertThat(lookup.findMatches(returning.getDui(), returning.getMobilePhone(), "A9C3·77E1·0B5D")).isEmpty();
	}

	@Test
	void aRequestMatchingSeveralTypesAndListsGetsEveryMatchHeaviestFirst() throws Exception {
		add(WATCH, "DOCUMENT", "04567891-2", "En vigilancia");
		add(FRAUD, "PHONE", "7845-2310", "Teléfono de fraude");
		add(FRAUD, "DEVICE", "huella-1", "Dispositivo de fraude");

		List<RiskListMatch> matches = lookup.findMatches("04567891-2", "7845-2310", "huella-1");

		assertThat(matches).extracting(RiskListMatch::weight).containsExactly(100, 100, 30);
		// a tie in weight is broken by the list code and then by the order the entries were added
		assertThat(matches).extracting(RiskListMatch::dataType)
			.containsExactly(RiskListDataType.PHONE, RiskListDataType.DEVICE, RiskListDataType.DOCUMENT);
	}

	@Test
	void theSameValueOnTwoListsMatchesBoth() throws Exception {
		add(FRAUD, "DOCUMENT", "04567891-2", "Fraude");
		add(WATCH, "DOCUMENT", "04567891-2", "Vigilancia");

		assertThat(lookup.findMatches("04567891-2", null, null)).extracting(RiskListMatch::listCode)
			.containsExactly("FRAUDE_CONFIRMADO", "VIGILANCIA");
	}

	@Test
	void theSameDigitsOfAnotherTypeDoNotMatch() throws Exception {
		add(FRAUD, "DEVICE", "78452310", "Huella con dígitos");

		assertThat(lookup.findMatches(null, "7845-2310", null)).isEmpty();
	}

	@Test
	void valuesThatAreMissingOrMalformedMatchNothing() throws Exception {
		add(FRAUD, "DOCUMENT", "04567891-2", "Fraude");

		assertThat(lookup.findMatches(null, null, null)).isEmpty();
		assertThat(lookup.findMatches(" ", "", "  ")).isEmpty();
		assertThat(lookup.findMatches("no-es-un-dui", "123", "x".repeat(200))).isEmpty();
		assertThat(lookup.findMatches("04567892-0", null, null)).isEmpty();
	}

	@Test
	void anEntryTakenOffNoLongerMatchesAndItsHistoryStays() throws Exception {
		long id = add(FRAUD, "DOCUMENT", "04567891-2", "Fraude confirmado");
		assertThat(lookup.findMatches("04567891-2", null, null)).hasSize(1);

		deactivate(id, "{\"reason\": \"Se descartó el fraude\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(false))
			.andExpect(jsonPath("$.deactivatedBy").value(admin.getFullName()))
			.andExpect(jsonPath("$.deactivatedAt").exists())
			.andExpect(jsonPath("$.deactivationReason").value("Se descartó el fraude"));

		assertThat(lookup.findMatches("04567891-2", null, null)).isEmpty();
		// it is not deleted
		assertThat(jdbc.queryForObject("select count(*) from risk_list_entry where id = ? and not active", Integer.class, id))
			.isEqualTo(1);
		mvc.perform(get(FRAUD))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].active").value(false))
			.andExpect(jsonPath("$[0].reason").value("Fraude confirmado"));
		mvc.perform(get(LISTS + "/entries/{id}/changes", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].action").value("ADDED"))
			.andExpect(jsonPath("$[0].changedBy").value(admin.getFullName()))
			.andExpect(jsonPath("$[0].changedAt").exists())
			.andExpect(jsonPath("$[0].reason").value("Fraude confirmado"))
			.andExpect(jsonPath("$[1].action").value("DEACTIVATED"))
			.andExpect(jsonPath("$[1].changedBy").value(admin.getFullName()))
			.andExpect(jsonPath("$[1].reason").value("Se descartó el fraude"));
		// the same value can be added again later: it is a new entry
		long again = add(FRAUD, "DOCUMENT", "04567891-2", "Reincidente");
		assertThat(again).isNotEqualTo(id);
		assertThat(lookup.findMatches("04567891-2", null, null)).hasSize(1);
	}

	@Test
	void everyChangeIsAlsoInTheAccessAuditWithoutTheValue() throws Exception {
		long id = add(FRAUD, "DOCUMENT", "04567891-2", "Fraude confirmado");
		deactivate(id, "{\"reason\": \"Descartado\"}").andExpect(status().isOk());

		List<String> audits = jdbc.queryForList(
				"select action || ' ' || entity_name || ' ' || entity_id from access_audit where user_id = ? order by id",
				String.class, admin.getId());
		assertThat(audits).filteredOn(a -> a.startsWith("RISK_LIST"))
			.containsExactly("RISK_LIST_ENTRY_ADD risk-list-entry " + id, "RISK_LIST_ENTRY_REMOVE risk-list-entry " + id);
	}

	@Test
	void theValueIsStoredOnlyAsAHashAndNeverReturned() throws Exception {
		String response = mvc.perform(post(FRAUD).contentType(MediaType.APPLICATION_JSON)
			.content(entry("DOCUMENT", "04567891-2", "Fraude confirmado")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.list.code").value("FRAUDE_CONFIRMADO"))
			.andExpect(jsonPath("$.list.weight").value(100))
			.andExpect(jsonPath("$.dataType").value("DOCUMENT"))
			.andExpect(jsonPath("$.active").value(true))
			.andExpect(jsonPath("$.addedBy").value(admin.getFullName()))
			.andExpect(jsonPath("$.addedAt").exists())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String hash = jdbc.queryForObject("select value_hash from risk_list_entry", String.class);

		assertThat(hash).matches("[0-9a-f]{64}");
		assertThat(response).doesNotContain("04567891").doesNotContain("045678912").doesNotContain(hash);
		assertThat(mvc.perform(get(FRAUD)).andReturn().getResponse().getContentAsString()).doesNotContain("04567891")
			.doesNotContain(hash);
		assertThat(JsonPath.<List<String>>read(response, "$.*~")).doesNotContain("value", "valueHash", "hash");
		// nowhere in the entry rows is the value in the clear
		assertThat(jdbc.queryForObject("select count(*) from risk_list_entry where reason like '%04567891%'",
				Integer.class)).isZero();
	}

	@Test
	void aValueThatIsNotOfItsTypeOrIsAlreadyActiveIsRefused() throws Exception {
		send(FRAUD, entry("DOCUMENT", "12345", "Fraude")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(containsString("not a valid document")));
		send(FRAUD, entry("PHONE", "2245-2310", "Fraude")).andExpect(status().isBadRequest());
		send(FRAUD, entry("DEVICE", "   ", "Fraude")).andExpect(status().isBadRequest());
		send(FRAUD, "{\"dataType\": \"EMAIL\", \"value\": \"a@b.c\", \"reason\": \"Fraude\"}")
			.andExpect(status().isBadRequest());
		send(FRAUD, "{\"dataType\": \"DOCUMENT\", \"value\": \"04567891-2\"}").andExpect(status().isBadRequest());
		send(FRAUD, entry("DOCUMENT", "04567891-2", "<script>")).andExpect(status().isBadRequest());
		assertThat(jdbc.queryForObject("select count(*) from risk_list_entry", Integer.class)).isZero();

		add(FRAUD, "DOCUMENT", "04567891-2", "Fraude");
		send(FRAUD, entry("DOCUMENT", "045678912", "Otra vez")).andExpect(status().isConflict());
	}

	@Test
	void anUnknownListOrEntryIsNotFoundAndAnInactiveEntryCannotBeTakenOffAgain() throws Exception {
		send(LISTS + "/NO_EXISTE/entries", entry("DOCUMENT", "04567891-2", "Fraude")).andExpect(status().isNotFound());
		mvc.perform(get(LISTS + "/NO_EXISTE/entries")).andExpect(status().isNotFound());
		deactivate(999999, "{\"reason\": \"Descartado\"}").andExpect(status().isNotFound());
		mvc.perform(get(LISTS + "/entries/999999/changes")).andExpect(status().isNotFound());

		long id = add(FRAUD, "DOCUMENT", "04567891-2", "Fraude");
		deactivate(id, "{\"reason\": \"\"}").andExpect(status().isBadRequest());
		deactivate(id, "{\"reason\": \"Descartado\"}").andExpect(status().isOk());
		deactivate(id, "{\"reason\": \"Otra vez\"}").andExpect(status().isConflict());
	}

	@Test
	void onlyAdministratorsCanUseTheLists() throws Exception {
		MockMvc analyst = auth.mockMvcAs(context, auth.user(ConsoleRole.FRAUD_ANALYST));
		long id = add(FRAUD, "DOCUMENT", "04567891-2", "Fraude");

		analyst.perform(get(LISTS)).andExpect(status().isForbidden());
		analyst.perform(get(FRAUD)).andExpect(status().isForbidden());
		analyst.perform(post(FRAUD).contentType(MediaType.APPLICATION_JSON).content(entry("DEVICE", "huella", "Fraude")))
			.andExpect(status().isForbidden());
		analyst.perform(post(LISTS + "/entries/{id}/deactivate", id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\": \"Descartado\"}")).andExpect(status().isForbidden());
		analyst.perform(get(LISTS + "/entries/{id}/changes", id)).andExpect(status().isForbidden());
		assertThat(lookup.findMatches("04567891-2", null, null)).hasSize(1);
	}

	@Test
	void theDatabaseNeverDeletesAnEntry() throws Exception {
		long id = add(FRAUD, "DOCUMENT", "04567891-2", "Fraude");

		assertThatThrownBy(() -> jdbc.update("delete from risk_list_entry where id = ?", id))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("only deactivated");
	}

	@Test
	void theDatabaseDoesNotLetAnEntryBeEditedExceptToDeactivateIt() throws Exception {
		long id = add(FRAUD, "DOCUMENT", "04567891-2", "Fraude");

		assertThatThrownBy(() -> jdbc.update("update risk_list_entry set reason = 'Otro' where id = ?", id))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("only deactivated");
	}

	@Test
	void anInactiveEntryCannotComeBackInTheDatabase() throws Exception {
		long id = add(FRAUD, "DOCUMENT", "04567891-2", "Fraude");
		deactivate(id, "{\"reason\": \"Descartado\"}").andExpect(status().isOk());

		assertThatThrownBy(() -> jdbc.update(
				"update risk_list_entry set active = true, deactivated_by = null, deactivated_at = null, deactivation_reason = null where id = ?",
				id))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("only deactivated");
	}

	@Test
	void aDeactivationNeedsWhoWhenAndWhyInTheDatabase() throws Exception {
		long id = add(FRAUD, "DOCUMENT", "04567891-2", "Fraude");

		assertThatThrownBy(() -> jdbc.update("update risk_list_entry set active = false where id = ?", id))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_risk_list_entry_deactivation");
	}

	@Test
	void theChangeLogCannotBeDeleted() throws Exception {
		long id = add(FRAUD, "DOCUMENT", "04567891-2", "Fraude");

		assertThatThrownBy(() -> jdbc.update("delete from risk_list_entry_change where entry_id = ?", id))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be modified or deleted");
	}

	@Test
	void theChangeLogCannotBeModified() throws Exception {
		long id = add(FRAUD, "DOCUMENT", "04567891-2", "Fraude");

		assertThatThrownBy(() -> jdbc.update("update risk_list_entry_change set reason = 'Otro' where entry_id = ?", id))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be modified or deleted");
	}

	private Customer customer(String dui, String phone, String firstNames, String lastNames) {
		Customer customer = new Customer();
		customer.setDui(dui);
		customer.setMobilePhone(phone);
		customer.setFirstNames(firstNames);
		customer.setLastNames(lastNames);
		return customers.saveAndFlush(customer);
	}

	private long add(String url, String dataType, String value, String reason) throws Exception {
		String body = send(url, entry(dataType, value, reason)).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private String entry(String dataType, String value, String reason) {
		return "{\"dataType\": \"" + dataType + "\", \"value\": \"" + value + "\", \"reason\": \"" + reason + "\"}";
	}

	private ResultActions send(String url, String body) throws Exception {
		return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions deactivate(long id, String body) throws Exception {
		return mvc.perform(post(LISTS + "/entries/{id}/deactivate", id).contentType(MediaType.APPLICATION_JSON).content(body));
	}

}
