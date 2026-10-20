# RNPN check (VDI-111, story VDI-17)

When a request is submitted (`POST /api/onboarding/requests/{id}/submit`) the backend checks the data of the DUI the
customer **confirmed** (number, first names, last names and birth date) against the RNPN. There is no public RNPN API,
so for now the service is **simulated**; the rest of the application only knows the `RnpnClient` interface, so the real
service can replace it without touching anything else.

## Results

| Result | Meaning | What happens to the request | Points of the `RNPN` signal |
|---|---|---|---|
| `MATCH` | The RNPN holds the same data. | Continues as usual. | 0 |
| `NO_MATCH` | The RNPN answered and some data differs (`mismatchedFields`). | Continues; the analyst sees which data differs. | all (15 in score model v2) |
| `UNAVAILABLE` | The RNPN did not answer after the retries. | Status `AWAITING_RNPN`: not approved automatically, no account can be opened. | half (8 of 15) |

No result approves or rejects a request by itself: the signal only adds to the score.

## Retries

If the RNPN does not answer, it is asked again until `app.rnpn.attempts` tries in total (3 by default), waiting
`app.rnpn.retry-delay` after the first failure (500 ms) and twice as long after each next one (500 ms, then 1 s). The
RNPN is asked **outside any database transaction**, so a slow answer does not hold a connection.

## What is stored

Table `rnpn_check`, append-only (the database refuses updates and deletes; only deleting the whole request cascades):
the result, the provider, the number of `attempts`, the `mismatched_fields`, the `failure` when the RNPN did not answer,
and the date and time. The timeline gets an `RNPN_CHECKED` event.

The console detail (`GET /api/console/requests/{id}`) returns the last check as `rnpn`:
`outcome`, `simulated` (true while the RNPN is the stand-in, which the console must say), `provider`, `attempts`,
`mismatchedFields` and `checkedAt`. The request list filters the waiting requests with `?status=AWAITING_RNPN`.

The mobile app gets nothing about the RNPN: after submitting it only reads `status`, which is `AWAITING_RNPN` when the
request waits. It must not mention the RNPN or ask the person to try again (the retries are the API's).

If a request waits for the RNPN **and** its score is above the threshold, the status stays `AWAITING_RNPN`; the decision
to hold it for enhanced review is still written to the audit log and the timeline.

## Test DUIs of the simulated RNPN

The answer depends only on the DUI number of the basic data and the confirmed DUI:

| DUI | Result |
|---|---|
| `00000020-4` | `NO_MATCH`, with `LAST_NAMES` and `BIRTH_DATE` as mismatched fields |
| `00000030-1` | `UNAVAILABLE` (it never answers, so the request waits after 3 tries) |
| any other DUI, for example `01234567-8` | `MATCH` |

Both are valid DUI numbers (the check digit is correct). The lists are configurable with
`APP_RNPN_SIMULATED_NO_MATCH_DUIS` and `APP_RNPN_SIMULATED_UNAVAILABLE_DUIS` (comma-separated).

## Configuration

| Variable | Default | |
|---|---|---|
| `APP_RNPN_PROVIDER` | `simulated` | The only provider for now. |
| `APP_RNPN_ATTEMPTS` | `3` | Total tries, at least 1. |
| `APP_RNPN_RETRY_DELAY` | `500ms` | Wait after the first failed try; it doubles after each next one. |

## Not done here

- A way to release a request that waits (a new try of the RNPN check, or an analyst deciding): a request in
  `AWAITING_RNPN` does not move by itself.
- `GET /resume` still answers `COMPLETED` for a request that waits, like it does for one held for enhanced review. If the
  app has to tell them apart when the person reopens it (VDI-112), that needs a decision with the app team.
