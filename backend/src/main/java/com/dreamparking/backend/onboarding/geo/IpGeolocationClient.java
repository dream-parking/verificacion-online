package com.dreamparking.backend.onboarding.geo;

import java.net.InetAddress;

/** Resolves the approximate location of a public IP address. */
public interface IpGeolocationClient {

	/** @throws GeolocationException when the provider is off, fails, times out or does not know the address */
	IpGeolocation locate(InetAddress ip) throws GeolocationException;

}
