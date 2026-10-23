package com.dreamparking.backend.rnpn.client;

import java.time.LocalDate;

/** The data of the DUI the customer confirmed, which the RNPN is asked about. */
public record RnpnQuery(String dui, String firstNames, String lastNames, LocalDate birthDate) {
}
