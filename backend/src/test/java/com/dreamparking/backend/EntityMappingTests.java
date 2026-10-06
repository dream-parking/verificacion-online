package com.dreamparking.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.alert.Alert;
import com.dreamparking.backend.alert.AlertCriticality;
import com.dreamparking.backend.alert.AlertInboxItem;
import com.dreamparking.backend.alert.AlertStatus;
import com.dreamparking.backend.onboarding.OnboardingRequest;
import com.dreamparking.backend.onboarding.OnboardingSession;
import com.dreamparking.backend.onboarding.OnboardingStep;
import com.dreamparking.backend.onboarding.RequestEvent;
import com.dreamparking.backend.onboarding.RequestEventType;
import com.dreamparking.backend.onboarding.RequestSignals;
import com.dreamparking.backend.onboarding.RequestStatus;
import com.dreamparking.backend.onboarding.RequestStep;
import com.dreamparking.backend.onboarding.RequestStepId;
import com.dreamparking.backend.onboarding.TypingPace;
import com.dreamparking.backend.risk.RiskAssessment;
import com.dreamparking.backend.risk.RiskLevel;

/** Reads the demo data through the entities and writes new rows, so every column type mapping is exercised. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class EntityMappingTests {

	static final UUID DEMO_REQUEST = UUID.fromString("00000000-0000-0000-0000-000000000418");

	@Autowired
	EntityManager em;

	@Test
	void readsDemoRequest() throws Exception {
		OnboardingRequest request = em.find(OnboardingRequest.class, DEMO_REQUEST);
		assertThat(request.getNumber()).isEqualTo("SOL-2026-00418");
		assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
		assertThat(request.getRiskLevel()).isEqualTo(RiskLevel.LOW);
		assertThat(request.getDui()).isEqualTo("04812377-5");
		assertThat(request.getCustomer().getMobilePhone()).isEqualTo("7845-2310");

		RequestStep step = em.find(RequestStep.class, new RequestStepId(DEMO_REQUEST, OnboardingStep.PRIVACY_NOTICE));
		assertThat(step.getDurationSeconds()).isEqualTo(68);

		OnboardingSession session = em.createQuery(
				"from OnboardingSession s where s.request.id = :id", OnboardingSession.class)
			.setParameter("id", DEMO_REQUEST)
			.getSingleResult();
		assertThat(session.getIp()).isEqualTo(InetAddress.getByName("190.5.142.77"));
		assertThat(session.getTypingPace()).isEqualTo(TypingPace.NORMAL);

		RiskAssessment assessment = em.createQuery(
				"from RiskAssessment a where a.request.id = :id and a.current", RiskAssessment.class)
			.setParameter("id", DEMO_REQUEST)
			.getSingleResult();
		assertThat(assessment.getRule().getCode()).isEqualTo("R-01");

		RequestEvent scoreEvent = em.createQuery(
				"from RequestEvent e where e.request.id = :id and e.type = :type", RequestEvent.class)
			.setParameter("id", DEMO_REQUEST)
			.setParameter("type", RequestEventType.SCORE_ASSIGNED)
			.getSingleResult();
		assertThat(scoreEvent.getData()).containsEntry("regla", "R-01");
	}

	@Test
	void readsViews() {
		RequestSignals signals = em.find(RequestSignals.class, DEMO_REQUEST);
		assertThat(signals.getTotalDurationSeconds()).isEqualTo(292L);
		assertThat(signals.getTypingPace()).isEqualTo(TypingPace.NORMAL);

		AlertInboxItem item = em.createQuery("from AlertInboxItem", AlertInboxItem.class).getSingleResult();
		assertThat(item.getCriticality()).isEqualTo(AlertCriticality.CRITICAL);
		assertThat(item.getStatus()).isEqualTo(AlertStatus.UNASSIGNED);
		assertThat(item.getMaskedAccount()).isEqualTo("•••• 4821");
	}

	@Test
	void writesEnumsJsonAndGeneratedColumns() {
		OnboardingRequest request = new OnboardingRequest();
		em.persist(request);

		RequestStep step = new RequestStep(request, OnboardingStep.BASIC_DATA, Instant.parse("2026-10-06T10:00:00Z"));
		step.setCompletedAt(Instant.parse("2026-10-06T10:01:30Z"));
		em.persist(step);

		RequestEvent event = new RequestEvent();
		event.setRequest(request);
		event.setType(RequestEventType.REQUEST_STARTED);
		event.setDescription("Request started");
		event.setData(Map.of("source", "test"));
		em.persist(event);

		Alert alert = em.createQuery("from Alert", Alert.class).getSingleResult();
		alert.setCriticality(AlertCriticality.LOW);
		em.flush();
		em.clear();

		assertThat(em.find(OnboardingRequest.class, request.getId()).getStatus()).isEqualTo(RequestStatus.IN_PROGRESS);
		assertThat(em.find(RequestStep.class, step.getId()).getDurationSeconds()).isEqualTo(90);
		assertThat(em.find(RequestEvent.class, event.getId()).getData()).containsEntry("source", "test");
		assertThat(em.find(Alert.class, alert.getId()).getSeverity()).isEqualTo((short) 3);
	}

}
