package com.dreamparking.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.onboarding.entity.enums.RequestStatus;
import com.dreamparking.backend.onboarding.repository.OnboardingRequestRepository;
import com.dreamparking.backend.risklist.dto.RiskListMatch;
import com.dreamparking.backend.risklist.entity.enums.RiskListDataType;
import com.dreamparking.backend.risklist.service.RiskListLookupService;

/**
 * VDI-144: an analyst registers how a case ended. A confirmed fraud puts the DUI, the phone and the device of the request
 * on the internal risk list (VDI-142); a false positive only records it; correcting an outcome adds a row and takes off
 * what the earlier one had put on the list.
 */
@Import({ TestcontainersConfiguration.class, TestAuth.class })
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class RequestOutcomeApiTests {

	static final String DUI = "04567891-2";

	static final String PHONE = "7845-2310";

	static final String FINGERPRINT = "a9c3-77e1-0b5d";

	static final String FRAUD = "FRAUDE_CONFIRMADO";

	static final String FALSE_POSITIVE = "FALSO_POSITIVO";

	@Autowired
	WebApplicationContext context;

	@Autowired
	TestAuth auth;

	@Autowired
	RiskListLookupService lookup;

	@Autowired
	OnboardingRequestRepository requests;

	@Autowired
	CustomerRepository customers;

	@Autowired
	DeviceRepository devices;

	@Autowired
	JdbcTemplate jdbc;

	ConsoleUser analyst;

	MockMvc mvc;

	OnboardingRequest request;

	@BeforeEach
	void signInWithARequest() {
		analyst = auth.user(ConsoleRole.FRAUD_ANALYST);
		mvc = auth.mockMvcAs(context, analyst);
		request = submitted(request(DUI, PHONE, FINGERPRINT));
	}

	@Test
	void aConfirmedFraudPutsTheDocumentThePhoneAndTheDeviceOnTheList() throws Exception {
		assertThat(found()).isEmpty();

		register(request, FRAUD, "El titular negó haber abierto la cuenta").andExpect(status().isCreated())
			.andExpect(jsonPath("$.requestId").value(request.getId().toString()))
			.andExpect(jsonPath("$.outcome").value(FRAUD))
			.andExpect(jsonPath("$.reason").value("El titular negó haber abierto la cuenta"))
			.andExpect(jsonPath("$.registeredBy").value(analyst.getFullName()))
			.andExpect(jsonPath("$.registeredAt").exists())
			.andExpect(jsonPath("$.listed").value(contains("DOCUMENT", "PHONE", "DEVICE")))
			.andExpect(jsonPath("$.revoked").value(empty()));

		assertThat(found()).extracting(RiskListMatch::dataType)
			.containsExactlyInAnyOrder(RiskListDataType.DOCUMENT, RiskListDataType.PHONE, RiskListDataType.DEVICE);
		assertThat(found()).extracting(RiskListMatch::listCode).containsOnly("FRAUDE_CONFIRMADO");
		// the entries say where they come from, and the value is nowhere in the clear
		assertThat(jdbc.queryForList("select reason from risk_list_entry", String.class)).hasSize(3)
			.allSatisfy(reason -> assertThat(reason).startsWith("Fraude confirmado en la solicitud " + request.getNumber())
				.doesNotContain("04567891")
				.doesNotContain("7845"));
		assertThat(jdbc.queryForObject("select count(*) from risk_list_entry_change where action = 'ADDED'", Integer.class))
			.isEqualTo(3);
		assertThat(jdbc.queryForObject("select count(*) from request_outcome_entry where link = 'LISTED'", Integer.class))
			.isEqualTo(3);
	}

	@Test
	void theDataIsFoundForAnotherRequestOfTheSamePersonOrDevice() throws Exception {
		register(request, FRAUD, "Fraude confirmado").andExpect(status().isCreated());

		// another request, another name: the same device is enough
		assertThat(lookup.findMatches("09876543-2", "6123-4567", FINGERPRINT)).extracting(RiskListMatch::dataType)
			.containsExactly(RiskListDataType.DEVICE);
	}

	@Test
	void aFalsePositiveOnlyRecordsAndLeavesTheListsAlone() throws Exception {
		register(request, FALSE_POSITIVE, "Era un cliente legítimo con un teléfono nuevo").andExpect(status().isCreated())
			.andExpect(jsonPath("$.outcome").value(FALSE_POSITIVE))
			.andExpect(jsonPath("$.listed").value(empty()))
			.andExpect(jsonPath("$.revoked").value(empty()));

		assertThat(found()).isEmpty();
		assertThat(jdbc.queryForObject("select count(*) from risk_list_entry", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from request_outcome", Integer.class)).isEqualTo(1);
	}

	@Test
	void correctingAFraudToAFalsePositiveTakesTheDataOffAndKeepsBothRows() throws Exception {
		register(request, FRAUD, "Fraude confirmado").andExpect(status().isCreated());
		assertThat(found()).hasSize(3);

		register(request, FALSE_POSITIVE, "Era el titular, lo confirmó en sucursal").andExpect(status().isCreated())
			.andExpect(jsonPath("$.listed").value(empty()))
			.andExpect(jsonPath("$.revoked").value(contains("DOCUMENT", "PHONE", "DEVICE")));

		assertThat(found()).isEmpty();
		// the two outcomes are still there, untouched
		mvc.perform(get(url(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].outcome").value(FRAUD))
			.andExpect(jsonPath("$[0].reason").value("Fraude confirmado"))
			.andExpect(jsonPath("$[0].listed").value(contains("DOCUMENT", "PHONE", "DEVICE")))
			.andExpect(jsonPath("$[0].revoked").value(empty()))
			.andExpect(jsonPath("$[1].outcome").value(FALSE_POSITIVE))
			.andExpect(jsonPath("$[1].listed").value(empty()))
			.andExpect(jsonPath("$[1].revoked").value(contains("DOCUMENT", "PHONE", "DEVICE")));
		// the entries are deactivated, not deleted, and the log of each says who took it off and why
		assertThat(jdbc.queryForObject("select count(*) from risk_list_entry where not active", Integer.class)).isEqualTo(3);
		assertThat(jdbc.queryForList("select reason from risk_list_entry_change where action = 'DEACTIVATED'", String.class))
			.hasSize(3)
			.allSatisfy(reason -> assertThat(reason).startsWith("Desenlace corregido a FALSO_POSITIVO en la solicitud "
					+ request.getNumber() + ": Era el titular"));
	}

	@Test
	void registeringTheSameOutcomeTwiceDoesNotDuplicateEntries() throws Exception {
		register(request, FRAUD, "Fraude confirmado").andExpect(status().isCreated());
		register(request, FRAUD, "Lo confirmó otro analista").andExpect(status().isCreated())
			.andExpect(jsonPath("$.listed").value(empty()))
			.andExpect(jsonPath("$.revoked").value(empty()));

		assertThat(found()).hasSize(3);
		assertThat(jdbc.queryForObject("select count(*) from risk_list_entry", Integer.class)).isEqualTo(3);
		assertThat(jdbc.queryForObject("select count(*) from request_outcome", Integer.class)).isEqualTo(2);

		// and a later correction still takes off what the first one put there
		register(request, FALSE_POSITIVE, "Descartado").andExpect(status().isCreated())
			.andExpect(jsonPath("$.revoked.length()").value(3));
		assertThat(found()).isEmpty();
	}

	@Test
	void aFalsePositiveTwiceRevokesNothingTheSecondTime() throws Exception {
		register(request, FRAUD, "Fraude confirmado").andExpect(status().isCreated());
		register(request, FALSE_POSITIVE, "Descartado").andExpect(status().isCreated());
		register(request, FALSE_POSITIVE, "Descartado de nuevo").andExpect(status().isCreated())
			.andExpect(jsonPath("$.revoked").value(empty()));

		assertThat(jdbc.queryForObject("select count(*) from risk_list_entry_change where action = 'DEACTIVATED'",
				Integer.class))
			.isEqualTo(3);
	}

	@Test
	void aFraudAgainAfterTheCorrectionListsTheDataAsNewEntries() throws Exception {
		register(request, FRAUD, "Fraude confirmado").andExpect(status().isCreated());
		register(request, FALSE_POSITIVE, "Descartado").andExpect(status().isCreated());
		register(request, FRAUD, "Se volvió a confirmar").andExpect(status().isCreated())
			.andExpect(jsonPath("$.listed").value(contains("DOCUMENT", "PHONE", "DEVICE")));

		assertThat(found()).hasSize(3);
		assertThat(jdbc.queryForObject("select count(*) from risk_list_entry", Integer.class)).isEqualTo(6);
	}

	@Test
	void whatWasAlreadyOnTheListByHandIsNotRepeatedNorTakenOffByTheCorrection() throws Exception {
		MockMvc admin = auth.mockMvcAs(context, auth.user(ConsoleRole.ADMIN));
		admin.perform(post(RiskListApiTests.FRAUD).contentType(MediaType.APPLICATION_JSON)
			.content("{\"dataType\": \"DOCUMENT\", \"value\": \"" + DUI + "\", \"reason\": \"Ya estaba\"}"))
			.andExpect(status().isCreated());

		register(request, FRAUD, "Fraude confirmado").andExpect(status().isCreated())
			.andExpect(jsonPath("$.listed").value(contains("PHONE", "DEVICE")));
		register(request, FALSE_POSITIVE, "Descartado").andExpect(status().isCreated())
			.andExpect(jsonPath("$.revoked").value(contains("PHONE", "DEVICE")));

		assertThat(found()).extracting(RiskListMatch::dataType).containsExactly(RiskListDataType.DOCUMENT);
	}

	@Test
	void theDataOfTheCustomerIsUsedWhenTheRequestHasNone() throws Exception {
		Customer customer = customer("01234567-8", "6123-4567");
		OnboardingRequest bare = request(null, null, null);
		bare.setCustomer(customer);
		requests.saveAndFlush(bare);

		register(bare, FRAUD, "Fraude confirmado").andExpect(status().isCreated())
			.andExpect(jsonPath("$.listed").value(contains("DOCUMENT", "PHONE")));

		assertThat(lookup.findMatches("01234567-8", "6123-4567", null)).hasSize(2);
	}

	@Test
	void aRequestWithoutDataStillRecordsTheOutcome() throws Exception {
		OnboardingRequest bare = request(null, null, null);

		register(bare, FRAUD, "Fraude confirmado").andExpect(status().isCreated())
			.andExpect(jsonPath("$.listed").value(empty()));

		assertThat(jdbc.queryForObject("select count(*) from request_outcome", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from risk_list_entry", Integer.class)).isZero();
	}

	@Test
	void theRequestWithoutNumberIsNamedByItsId() throws Exception {
		// a request still in progress has no number yet
		OnboardingRequest unnumbered = request(DUI, null, null);

		register(unnumbered, FRAUD, "Fraude confirmado").andExpect(status().isCreated());

		assertThat(jdbc.queryForObject("select reason from risk_list_entry", String.class))
			.startsWith("Fraude confirmado en la solicitud " + unnumbered.getId());
	}

	@Test
	void theOutcomeIsInTheAccessAudit() throws Exception {
		register(request, FALSE_POSITIVE, "Descartado").andExpect(status().isCreated());

		assertThat(jdbc.queryForList(
				"select action || ' ' || entity_name || ' ' || entity_id from access_audit where user_id = ?", String.class,
				analyst.getId())).containsExactly("REGISTER_REQUEST_OUTCOME onboarding_request " + request.getId());
	}

	@Test
	void anAdministratorCanRegisterTooButAKycLeadCannot() throws Exception {
		MockMvc admin = auth.mockMvcAs(context, auth.user(ConsoleRole.ADMIN));
		MockMvc lead = auth.mockMvcAs(context, auth.user(ConsoleRole.KYC_LEAD));

		admin.perform(post(url(request)).contentType(MediaType.APPLICATION_JSON).content(body(FALSE_POSITIVE, "Descartado")))
			.andExpect(status().isCreated());
		lead.perform(post(url(request)).contentType(MediaType.APPLICATION_JSON).content(body(FRAUD, "Fraude")))
			.andExpect(status().isForbidden());
		// reading the history is open to every console user
		lead.perform(get(url(request))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
		assertThat(jdbc.queryForObject("select count(*) from request_outcome", Integer.class)).isEqualTo(1);
		assertThat(found()).isEmpty();
	}

	@Test
	void anUnknownRequestIsNotFound() throws Exception {
		String url = "/api/console/requests/" + UUID.randomUUID() + "/outcome";

		mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body(FRAUD, "Fraude")))
			.andExpect(status().isNotFound());
		mvc.perform(get(url)).andExpect(status().isNotFound());
	}

	@Test
	void anInvalidBodyIsRefusedAndRecordsNothing() throws Exception {
		send("{\"reason\": \"Sin desenlace\"}").andExpect(status().isBadRequest());
		send("{\"outcome\": \"SOSPECHOSO\", \"reason\": \"No existe\"}").andExpect(status().isBadRequest());
		send("{\"outcome\": \"" + FRAUD + "\"}").andExpect(status().isBadRequest());
		send(body(FRAUD, " ")).andExpect(status().isBadRequest());
		send(body(FRAUD, "<script>")).andExpect(status().isBadRequest());
		send(body(FRAUD, "x".repeat(151))).andExpect(status().isBadRequest());

		assertThat(jdbc.queryForObject("select count(*) from request_outcome", Integer.class)).isZero();
	}

	private ResultActions send(String body) throws Exception {
		return mvc.perform(post(url(request)).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions register(OnboardingRequest target, String outcome, String reason) throws Exception {
		return mvc.perform(post(url(target)).contentType(MediaType.APPLICATION_JSON).content(body(outcome, reason)));
	}

	private List<RiskListMatch> found() {
		return lookup.findMatches(DUI, PHONE, FINGERPRINT);
	}

	private static String url(OnboardingRequest target) {
		return "/api/console/requests/" + target.getId() + "/outcome";
	}

	private static String body(String outcome, String reason) {
		return "{\"outcome\": \"" + outcome + "\", \"reason\": \"" + reason + "\"}";
	}

	/** What a submitted request looks like: its customer and name, the six steps, its number and its date. */
	private OnboardingRequest submitted(OnboardingRequest created) {
		created.setCustomer(customer(created.getDui(), created.getMobilePhone()));
		created.setFirstNames("Nombre");
		created.setLastNames("Apellido");
		created.setStatus(RequestStatus.COMPLETED);
		created.setCompletedSteps((short) 6);
		created.setNumber("SOL-2026-" + String.format("%05d", ThreadLocalRandom.current().nextInt(1, 100000)));
		created.setSubmittedAt(Instant.now());
		return requests.saveAndFlush(created);
	}

	private Customer customer(String dui, String phone) {
		Customer customer = new Customer();
		customer.setDui(dui);
		customer.setMobilePhone(phone);
		customer.setFirstNames("Nombre");
		customer.setLastNames("Apellido");
		return customers.saveAndFlush(customer);
	}

	private OnboardingRequest request(String dui, String phone, String fingerprint) {
		OnboardingRequest created = new OnboardingRequest();
		created.setDui(dui);
		created.setMobilePhone(phone);
		if (fingerprint != null) {
			Device device = new Device();
			device.setFingerprint(fingerprint);
			created.setDevice(devices.saveAndFlush(device));
		}
		return requests.saveAndFlush(created);
	}

}
