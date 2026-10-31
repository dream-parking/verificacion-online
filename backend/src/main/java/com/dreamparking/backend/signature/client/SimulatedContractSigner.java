package com.dreamparking.backend.signature.client;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stand-in for the electronic signature service, which has no provider yet ({@code app.signature.provider=simulated},
 * the default). The answer depends only on the DUI number so QA and the app can try both results:
 * <ul>
 * <li>{@code 00000040-8} (by default): the signature is rejected.</li>
 * <li>Any other DUI: the contract is signed.</li>
 * </ul>
 * The list is configurable ({@code app.signature.simulated-rejected-duis}). Every signature it makes is stored with the
 * provider {@code simulated}, so it is never mistaken for a real one.
 */
@Component
@ConditionalOnProperty(name = "app.signature.provider", havingValue = "simulated", matchIfMissing = true)
public class SimulatedContractSigner implements ContractSigner {

	static final String REJECTION_REASON = "El servicio de firma simulado rechazó la firma de este DUI";

	private final Set<String> rejectedDuis;

	public SimulatedContractSigner(@Value("${app.signature.simulated-rejected-duis:00000040-8}") Set<String> rejectedDuis) {
		this.rejectedDuis = Set.copyOf(rejectedDuis);
	}

	@Override
	public SigningResult sign(SigningQuery query) {
		if (rejectedDuis.contains(query.dui())) {
			return SigningResult.rejected(REJECTION_REASON);
		}
		return SigningResult.accepted();
	}

	@Override
	public String provider() {
		return SIMULATED;
	}

}
