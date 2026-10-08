package com.dreamparking.backend.onboarding.geo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * What ip-api.com knows about an IP address (its {@code /json} response, success case). The JSON names are the
 * provider's; {@code asName} is its {@code as} field.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IpGeolocation(String status, String message, String country,
		@JsonProperty("countryCode") String countryCode, String region, @JsonProperty("regionName") String regionName,
		String city, String zip, Double lat, Double lon, String timezone, String isp, String org,
		@JsonProperty("as") String asName) {

	public static final String SUCCESS = "success";

	public boolean located() {
		return SUCCESS.equals(status) && lat != null && lon != null;
	}

}
