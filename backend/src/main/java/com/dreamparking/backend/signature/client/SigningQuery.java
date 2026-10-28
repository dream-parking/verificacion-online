package com.dreamparking.backend.signature.client;

import java.util.UUID;

/** What is sent to the signature service: who signs (by DUI) and which text, by its version and fingerprint. */
public record SigningQuery(UUID requestId, String dui, String contractVersion, String contractFingerprint) {
}
