package com.dreamparking.backend.rnpn.client;

/**
 * Contract with the RNPN (VDI-111). The rest of the application only knows this interface, so the simulated service
 * can be replaced by the real one without touching anything else.
 */
public interface RnpnClient {

	/**
	 * @return the RNPN's answer; data that does not match is a normal result, not an error
	 * @throws RnpnException when the RNPN is off, fails or times out
	 */
	RnpnResult check(RnpnQuery query) throws RnpnException;

	/** Name of the service, stored with each check. {@link #SIMULATED} while there is no real RNPN. */
	String provider();

	/** Provider name of the simulated service; the console labels these checks as simulated. */
	String SIMULATED = "simulated";

}
