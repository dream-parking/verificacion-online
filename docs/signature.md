# Electronic signature of the contract (VDI-130, story VDI-15)

The customer of an approved request signs the account contract from the app. There is no signature provider yet, so for
now the service is **simulated**, like the RNPN of VDI-111. The rest of the application only knows the `ContractSigner`
interface, so a real provider can replace it without touching anything else.

## The contract

The backend owns the text: `backend/src/main/resources/contracts/contrato-apertura-<version>.txt`, chosen by
`app.contract.version` (`2026.1`). The current text is a **test text** marked as such; the bank's legal team provides
the real one.

- `GET /api/onboarding/contract` returns `version`, `text` and `fingerprint` (SHA-256 of the text in UTF-8, lower-case
  hex). Line ends are read as `\n`, so the fingerprint does not depend on how git checked the file out.
- The app shows the text and signs by sending the `version` it showed. The backend computes the fingerprint itself, so a
  signature always proves which exact words were signed.
- A new version is a new file and a new `APP_CONTRACT_VERSION`. A request that tries to sign a version that is no longer
  in force gets `409` and must show the contract again.

## Who can sign

`POST /api/onboarding/requests/{id}/signature` with `{ "contractVersion": "2026.1" }`:

| Situation | Answer |
|---|---|
| Submitted and approved (`COMPLETED`), first time | `201`, the signature |
| Same request again | `409` already signed |
| `ENHANCED_REVIEW`, `AWAITING_RNPN`, `IN_PROGRESS` or `ABANDONED` | `409`, the same rule as opening an account |
| `contractVersion` is not the one in force | `409` |
| `contractVersion` missing or blank | `400` |
| Unknown request | `404` |
| The signature service rejects it | `422`; nothing stored as a signature, the customer can try again |

Two signatures of the same request at the same time: `contract_signature.request_id` is unique, so only one gets through
and the other answers `409`.

## What is stored

Table `contract_signature` (migration `V33__contract_signature.sql`), append-only: the database refuses updates and
deletes; only deleting the whole request cascades.

| Column | |
|---|---|
| `contract_version`, `contract_fingerprint` | Which text was signed |
| `signed_at` | Date and time of the signature |
| `provider` | `simulated` while there is no real provider |
| `ip` | Client address of the signing call |
| `device` | `User-Agent` of the signing call |
| `device_fingerprint` | Device fingerprint the app sent in `/signals`, if any |

The timeline gets `CONTRACT_SIGNED`, or `CONTRACT_SIGNATURE_REJECTED` with the reason. The app does not receive the IP
or the device.

## Test DUI of the simulated service

| DUI | Result |
|---|---|
| `00000040-8` | Rejected (`422`) |
| any other DUI | Signed |

`00000040-8` is a valid DUI and the simulated RNPN matches it, so its request is approved and reaches the signature. The
list is configurable with `APP_SIGNATURE_SIMULATED_REJECTED_DUIS` (comma-separated).

## Configuration

| Variable | Default | |
|---|---|---|
| `APP_CONTRACT_VERSION` | `2026.1` | Version of the contract in force; its text file must exist or the backend does not start. |
| `APP_SIGNATURE_PROVIDER` | `simulated` | The only provider for now. |
| `APP_SIGNATURE_SIMULATED_REJECTED_DUIS` | `00000040-8` | DUIs the simulated service rejects. |

## Not done here

- **Binding dossier (VDI-131):** the dossier is frozen when the customer signs. When VDI-131 reaches `main`, signing must
  call `BindingDossierService.freeze(requestId, new Signature(version, fingerprint, signedAt))` in the same transaction.
- **Migration order:** this is `V33` because `V31` (dossier, VDI-131) and `V32` (retention, VDI-134) are taken by work
  not merged yet. It has to reach `main` after them.
- **IP behind a proxy:** the IP is the client address the backend sees (`getRemoteAddr`), the same as the other signals.
  Behind the Azure ingress that may be the proxy unless forwarded headers are trusted.
- **Real provider:** a real service is a network call. It should then run outside the database transaction, like the RNPN.
