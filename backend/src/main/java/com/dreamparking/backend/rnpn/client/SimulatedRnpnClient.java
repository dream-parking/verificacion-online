package com.dreamparking.backend.rnpn.client;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.dreamparking.backend.rnpn.entity.enums.RnpnField;

/**
 * Stand-in for the RNPN, which has no public API ({@code app.rnpn.provider=simulated}, the default). The answer depends
 * only on the DUI number so QA and the app can try each result:
 * <ul>
 * <li>{@code 00000020-4} (by default): the RNPN answers that the last names and the birth date do not match.</li>
 * <li>{@code 00000030-1} (by default): the RNPN does not answer.</li>
 * <li>Any other DUI: the data matches.</li>
 * </ul>
 * The two lists are configurable ({@code app.rnpn.simulated-no-match-duis}, {@code app.rnpn.simulated-unavailable-duis}).
 * It must never be mistaken for the real RNPN: every check it makes is stored with the provider {@code simulated}.
 */
@Component
@ConditionalOnProperty(name = "app.rnpn.provider", havingValue = "simulated", matchIfMissing = true)
public class SimulatedRnpnClient implements RnpnClient {

	static final List<RnpnField> NO_MATCH_FIELDS = List.of(RnpnField.LAST_NAMES, RnpnField.BIRTH_DATE);

	private final Set<String> noMatchDuis;

	private final Set<String> unavailableDuis;

	public SimulatedRnpnClient(@Value("${app.rnpn.simulated-no-match-duis:00000020-4}") Set<String> noMatchDuis,
			@Value("${app.rnpn.simulated-unavailable-duis:00000030-1}") Set<String> unavailableDuis) {
		this.noMatchDuis = Set.copyOf(noMatchDuis);
		this.unavailableDuis = Set.copyOf(unavailableDuis);
	}

	@Override
	public RnpnResult check(RnpnQuery query) throws RnpnException {
		if (unavailableDuis.contains(query.dui())) {
			throw new RnpnException("The simulated RNPN does not answer for this DUI");
		}
		if (noMatchDuis.contains(query.dui())) {
			return RnpnResult.noMatch(NO_MATCH_FIELDS);
		}
		return RnpnResult.match();
	}

	@Override
	public String provider() {
		return SIMULATED;
	}

}
