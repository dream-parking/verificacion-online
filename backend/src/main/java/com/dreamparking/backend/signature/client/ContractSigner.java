package com.dreamparking.backend.signature.client;

/**
 * Contract with the electronic signature service (VDI-130). The rest of the application only knows this interface, so
 * the simulated service can be replaced by a real provider without touching anything else.
 */
public interface ContractSigner {

	/** Signs the contract for the customer, or says why it was rejected; a rejection is a normal answer, not an error. */
	SigningResult sign(SigningQuery query);

	/** Name of the service, stored with each signature. {@link #SIMULATED} while there is no real provider. */
	String provider();

	/** Provider name of the simulated service. */
	String SIMULATED = "simulated";

}
