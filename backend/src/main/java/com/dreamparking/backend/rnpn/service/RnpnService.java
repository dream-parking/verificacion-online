package com.dreamparking.backend.rnpn.service;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.dreamparking.backend.rnpn.client.RnpnClient;
import com.dreamparking.backend.rnpn.client.RnpnException;
import com.dreamparking.backend.rnpn.client.RnpnQuery;
import com.dreamparking.backend.rnpn.client.RnpnResult;
import com.dreamparking.backend.rnpn.entity.enums.RnpnField;
import com.dreamparking.backend.rnpn.entity.enums.RnpnOutcome;

/**
 * Asks the RNPN about the DUI data and retries when it does not answer, waiting longer each time (VDI-111). It does not
 * touch the database, so the caller runs it outside a transaction and a slow RNPN does not hold a connection of the
 * small pool. An RNPN that stays silent is not an error: the answer is {@link RnpnOutcome#UNAVAILABLE}.
 */
@Service
public class RnpnService {

	private static final Logger log = LoggerFactory.getLogger(RnpnService.class);

	private final RnpnClient client;

	private final int maxAttempts;

	private final Duration firstDelay;

	public RnpnService(RnpnClient client, @Value("${app.rnpn.attempts:3}") int maxAttempts,
			@Value("${app.rnpn.retry-delay:500ms}") Duration firstDelay) {
		if (maxAttempts < 1) {
			throw new IllegalArgumentException("app.rnpn.attempts must be at least 1");
		}
		this.client = client;
		this.maxAttempts = maxAttempts;
		this.firstDelay = firstDelay;
	}

	/** What the check ended in, to be stored with the request; {@code failure} only when the RNPN did not answer. */
	public record Answer(RnpnOutcome outcome, String provider, int attempts, List<RnpnField> mismatchedFields,
			String failure) {
	}

	public Answer check(RnpnQuery query) {
		String failure = null;
		Duration delay = firstDelay;
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				RnpnResult result = client.check(query);
				RnpnOutcome outcome = result.matches() ? RnpnOutcome.MATCH : RnpnOutcome.NO_MATCH;
				return new Answer(outcome, client.provider(), attempt, result.mismatchedFields(), null);
			}
			catch (RnpnException ex) {
				failure = ex.getMessage();
				log.warn("The RNPN did not answer (try {} of {}): {}", attempt, maxAttempts, failure);
			}
			if (attempt < maxAttempts && !pause(delay)) {
				return new Answer(RnpnOutcome.UNAVAILABLE, client.provider(), attempt, List.of(),
						"The check was interrupted");
			}
			delay = delay.multipliedBy(2);
		}
		return new Answer(RnpnOutcome.UNAVAILABLE, client.provider(), maxAttempts, List.of(), failure);
	}

	/** False when the thread was interrupted while waiting: the check stops and the interruption is kept. */
	private static boolean pause(Duration delay) {
		if (delay.isZero() || delay.isNegative()) {
			return true;
		}
		try {
			Thread.sleep(delay.toMillis());
			return true;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

}
