package com.dreamparking.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.account.entity.Account;
import com.dreamparking.backend.account.entity.enums.AccountStatus;
import com.dreamparking.backend.alert.entity.AlertHistory;
import com.dreamparking.backend.alert.entity.AlertType;
import com.dreamparking.backend.alert.entity.enums.AlertCriticality;
import com.dreamparking.backend.alert.entity.enums.AlertStatus;
import com.dreamparking.backend.catalog.entity.IncomeRange;
import com.dreamparking.backend.catalog.entity.IncomeSource;
import com.dreamparking.backend.catalog.entity.MonthlyAmountRange;
import com.dreamparking.backend.catalog.entity.PrivacyNotice;
import com.dreamparking.backend.catalog.entity.TransactionType;
import com.dreamparking.backend.console.entity.ConsoleUser;
import com.dreamparking.backend.console.entity.enums.ConsoleRole;
import com.dreamparking.backend.customer.entity.Customer;
import com.dreamparking.backend.customer.entity.Device;
import com.dreamparking.backend.onboarding.entity.PrivacyConsent;
import com.dreamparking.backend.onboarding.entity.RequestNumberSequence;

/** The reference and demo data (V3, V4, V6) read back through the entities, so a schema drift fails the build. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ReferenceDataMappingTests {

	@Autowired
	EntityManager em;

	@Test
	void readsCustomerDeviceAndAccount() {
		Customer customer = em.createQuery("from Customer where dui = '04812377-5'", Customer.class).getSingleResult();
		assertThat(customer.getFirstNames()).isEqualTo("Marta Alejandra");
		assertThat(customer.getLastNames()).isEqualTo("Rivas Cruz");
		assertThat(customer.getMobilePhone()).isEqualTo("7845-2310");
		assertThat(customer.getId()).isNotNull();
		assertThat(customer.getCreatedAt()).isNotNull();
		assertThat(customer.getUpdatedAt()).isNotNull();

		Device device = em.createQuery("from Device where fingerprint = 'd4f1·9a3c·e7b2'", Device.class).getSingleResult();
		assertThat(device.getModel()).isEqualTo("iPhone 15");
		assertThat(device.getOperatingSystem()).isEqualTo("iOS");
		assertThat(device.getFirstSeenAt()).isNotNull();
		assertThat(device.getLastSeenAt()).isNotNull();

		Account account = em.createQuery("from Account where lastFour = '4821'", Account.class).getSingleResult();
		assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(account.getNumberToken()).isEqualTo("tok_demo_4821");
		assertThat(account.getCustomer().getDui()).isEqualTo("04812377-5");
		assertThat(account.getRequest().getNumber()).isEqualTo("SOL-2026-00418");
		assertThat(account.getOpenedAt()).isNotNull();
		assertThat(account.getId()).isNotNull();
	}

	@Test
	void readsCatalogs() {
		IncomeRange range = em.find(IncomeRange.class, "500_1000");
		assertThat(range.getLabel()).isEqualTo("USD 500.01 a 1,000");
		assertThat(range.getMinUsd()).isEqualByComparingTo(new BigDecimal("500.01"));
		assertThat(range.getMaxUsd()).isEqualByComparingTo(new BigDecimal("1000"));
		assertThat(range.getSortOrder()).isEqualTo((short) 2);
		assertThat(range.getActive()).isTrue();
		assertThat(range.getCode()).isEqualTo("500_1000");

		MonthlyAmountRange monthly = em.find(MonthlyAmountRange.class, "MAS_1000");
		assertThat(monthly.getLabel()).isEqualTo("Más de USD 1,000");
		assertThat(monthly.getMinUsd()).isEqualByComparingTo(new BigDecimal("1000.01"));
		assertThat(monthly.getMaxUsd()).isNull();
		assertThat(monthly.getSortOrder()).isEqualTo((short) 4);
		assertThat(monthly.getActive()).isTrue();

		IncomeSource other = em.find(IncomeSource.class, "OTRO");
		assertThat(other.getLabel()).isEqualTo("Otro");
		assertThat(other.getSortOrder()).isEqualTo((short) 5);
		assertThat(other.getActive()).isTrue();

		TransactionType savings = em.find(TransactionType.class, "AHORRO");
		assertThat(savings.getLabel()).isEqualTo("Ahorro");
		assertThat(savings.getSortOrder()).isEqualTo((short) 4);
		assertThat(savings.getActive()).isTrue();

		PrivacyNotice notice = em.createQuery("from PrivacyNotice", PrivacyNotice.class).getSingleResult();
		assertThat(notice.getVersion()).isEqualTo("2026.1");
		assertThat(notice.getTextHash()).hasSize(64);
		assertThat(notice.getValidFrom()).isNotNull();
		assertThat(notice.getValidTo()).isNull();
		assertThat(notice.getId()).isNotNull();
	}

	@Test
	void readsPrivacyConsentAndRequestNumberSequence() {
		PrivacyConsent consent = em.find(PrivacyConsent.class, EntityMappingTests.DEMO_REQUEST);
		assertThat(consent.getSignalsAccepted()).isTrue();
		assertThat(consent.getIp().getHostAddress()).isEqualTo("190.5.142.77");
		assertThat(consent.getNotice().getVersion()).isEqualTo("2026.1");
		assertThat(consent.getRequest().getNumber()).isEqualTo("SOL-2026-00418");
		assertThat(consent.getAcceptedAt()).isNotNull();
		assertThat(consent.getRequestId()).isEqualTo(EntityMappingTests.DEMO_REQUEST);

		RequestNumberSequence sequence = em.find(RequestNumberSequence.class, (short) 2026);
		assertThat(sequence.getLastNumber()).isEqualTo(418);
		assertThat(sequence.getYear()).isEqualTo((short) 2026);
	}

	@Test
	void readsAlertTypesHistoryAndConsoleUsers() {
		AlertType type = em.find(AlertType.class, "PERFIL_EXCEDIDO");
		assertThat(type.getDefaultCriticality()).isEqualTo(AlertCriticality.CRITICAL);
		assertThat(type.getDescription()).contains("perfil declarado");
		assertThat(type.getCode()).isEqualTo("PERFIL_EXCEDIDO");

		ConsoleUser ana = em.createQuery("from ConsoleUser where email = 'abeltran@ceiba.example'", ConsoleUser.class)
			.getSingleResult();
		assertThat(ana.getRole()).isEqualTo(ConsoleRole.FRAUD_ANALYST);
		assertThat(ana.getInitials()).isEqualTo("AB");
		assertThat(ana.getFullName()).isEqualTo("Ana Beltrán");
		assertThat(ana.getJobTitle()).contains("fraude");
		assertThat(ana.getActive()).isTrue();
		assertThat(ana.getCreatedAt()).isNotNull();

		AlertHistory history = em
			.createQuery("from AlertHistory h where h.alert.account.lastFour = '7730'", AlertHistory.class)
			.getSingleResult();
		assertThat(history.getPreviousStatus()).isEqualTo(AlertStatus.UNASSIGNED);
		assertThat(history.getNewStatus()).isEqualTo(AlertStatus.ASSIGNED);
		assertThat(history.getAssignee().getEmail()).isEqualTo("abeltran@ceiba.example");
		assertThat(history.getActor().getEmail()).isEqualTo("abeltran@ceiba.example");
		assertThat(history.getOccurredAt()).isNotNull();
		assertThat(history.getComment()).isNull();
		assertThat(history.getId()).isNotNull();
		assertThat(history.getAlert().getReason()).contains("origen distinto");
	}

}
