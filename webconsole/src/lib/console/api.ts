// Client of the console API. The types follow docs/openapi.json; in responses the optional
// fields can come as null (see docs/integracion-api.md).

import type { components } from "./openapi";

/** The API URL comes only from NEXT_PUBLIC_API_URL (set by the deploy workflow); local development falls back to localhost. */
export const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

// ---- Contract types

/** Location permission on the phone (VDI-73): granted with a city, declined, or granted without a city. */
export type DeviceLocationStatus = "AVAILABLE" | "DENIED" | "UNAVAILABLE";

export type Role = "KYC_LEAD" | "FRAUD_ANALYST" | "ADMIN";
export type RiskLevel = "NOT_EVALUATED" | "LOW" | "PENDING_REVIEW" | "MEDIUM" | "HIGH";
/**
 * ENHANCED_REVIEW: submitted, but the score went above the threshold (VDI-117). AWAITING_RNPN: submitted, but the RNPN
 * did not answer after the retries (VDI-111). Neither is approved automatically.
 */
export type RequestStatus = "IN_PROGRESS" | "COMPLETED" | "ENHANCED_REVIEW" | "AWAITING_RNPN" | "ESCALATED" | "ABANDONED";
export type EscalationChannel = "VIDEO_CALL" | "BRANCH";
export type Criticality = "CRITICAL" | "HIGH" | "MEDIUM" | "LOW";
export type AlertStatus = "UNASSIGNED" | "ASSIGNED" | "IN_REVIEW" | "CLOSED";
export type Step = "PRIVACY_NOTICE" | "BASIC_DATA" | "IDENTITY_DOCUMENT" | "INCOME" | "EXPECTED_ACTIVITY" | "REVIEW";

export type ConsoleUser = {
  id: string;
  email: string;
  fullName: string;
  initials: string | null;
  jobTitle: string | null;
  role: Role;
  active: boolean;
};

export type LoginResponse = {
  accessToken: string;
  expiresIn: number;
  tokenType: string;
  user: ConsoleUser;
};

export type Page<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type RequestSummary = {
  id: string;
  number: string | null;
  name: string | null;
  date: string;
  transactionTypeLabel: string | null;
  monthlyAmountRangeLabel: string | null;
  riskLevel: RiskLevel;
  /** Score from 0 to 100 (VDI-84). Null without assessment or in the ones made by rule R-01; missing in older API versions. */
  score?: number | null;
  status: RequestStatus;
  completedSteps: number;
};

/** One version of the declared transactional profile (VDI-3). The API sends them oldest first; only the last is current. */
export type ProfileVersion = {
  version: number;
  current: boolean;
  transactionTypeCode: string;
  transactionTypeLabel: string | null;
  monthlyAmountRangeCode: string;
  monthlyAmountRangeLabel: string | null;
  /** Who declared it: CUSTOMER is the person, in the app, before submitting. */
  changedBy: "CUSTOMER" | null;
  declaredAt: string;
};

/** Data of a DUI, as read from the photo or as the person confirmed it. Plain dates come as "yyyy-mm-dd"; what was not read is null. */
export type DuiData = {
  dui: string | null;
  firstNames: string | null;
  lastNames: string | null;
  birthDate: string | null;
  issueDate?: string | null;
  expiryDate: string | null;
  gender?: string | null;
};

/** The DUI of a request (VDI-81): what was read, what the person confirmed and the fields they corrected. */
export type IdentityDocument = {
  ocrStatus: "READ" | "UNREADABLE" | "FAILED";
  read: DuiData | null;
  confirmed: DuiData | null;
  confirmedAt: string | null;
  /** Names of the fields the person changed from the reading: dui, firstNames, lastNames, birthDate, expiryDate. */
  correctedFields: string[] | null;
};

/**
 * Entry of the audit log of risk decisions (VDI-117), taken when the request was submitted. The score, the threshold and
 * the model version are the ones used at that moment; `reason` comes in English. Entries are never edited or deleted.
 */
export type RiskDecision = {
  id: number;
  decision: "AUTOMATIC_FLOW" | "ENHANCED_REVIEW" | "ESCALATED";
  reason: string | null;
  score: number;
  threshold: number;
  modelVersion: number;
  /** SYSTEM for the automatic rule; the analyst's name in an escalation. */
  decidedBy: string | null;
  decidedAt: string;
  /** Where the request was escalated (VDI-143); null in the automatic decisions. */
  channel: EscalationChannel | null;
};

export type RnpnOutcome = "MATCH" | "NO_MATCH" | "UNAVAILABLE";
export type RnpnField = "DUI" | "FIRST_NAMES" | "LAST_NAMES" | "BIRTH_DATE";

/**
 * Last check of the confirmed DUI data against the RNPN (VDI-111). `simulated` is true while the RNPN is the stand-in
 * service; `mismatchedFields` is empty unless the outcome is NO_MATCH; `attempts` also counts the unanswered tries.
 */
export type RnpnCheck = {
  outcome: RnpnOutcome;
  simulated: boolean;
  provider: string | null;
  attempts: number;
  mismatchedFields: RnpnField[] | null;
  checkedAt: string;
};

/**
 * The contract (docs/openapi.json) does not mark required or nullable fields, but the API leaves out or nulls whatever
 * it does not have yet. Every field below the top level is therefore optional and nullable, and the fields the API
 * always sends are re-required in `RequestDetail`.
 */
export type Loose<T> = T extends readonly (infer U)[] ? Loose<U>[] : T extends object ? { [K in keyof T]: Loose<T[K]> | null } : T;

type RequestDetailSchema = components["schemas"]["ConsoleRequestDetail"];
type AlwaysSent = "id" | "status" | "riskLevel" | "completedSteps" | "startedAt";
type LooseDetail = Loose<Omit<RequestDetailSchema, AlwaysSent>>;
type Item<T> = NonNullable<T> extends readonly (infer U)[] ? U : NonNullable<T>;

/** `T` with the fields in `K` never null: the ones the API always sends once the section exists. */
type Present<T, K extends keyof T> = Omit<T, K> & { [P in K]-?: NonNullable<T[P]> };

type Income = Present<Item<LooseDetail["income"]>, "sourceCode" | "rangeCode" | "registeredAt">;
type ExpectedActivity = Present<
  Item<LooseDetail["expectedActivity"]>,
  "transactionTypeCode" | "monthlyAmountRangeCode" | "registeredAt"
> & {
  versions: Present<
    Item<NonNullable<Item<LooseDetail["expectedActivity"]>["versions"]>>,
    "version" | "current" | "transactionTypeCode" | "monthlyAmountRangeCode" | "declaredAt"
  >[] | null;
};

/** Detail of a request; generated from docs/openapi.json (npm run gen:api). */
export type RequestDetail = Omit<LooseDetail, "income" | "expectedActivity" | "identityDocument" | "riskDecisions" | "rnpn" | "timeline"> &
  Required<Pick<RequestDetailSchema, AlwaysSent>> & {
    income: Income | null;
    expectedActivity: ExpectedActivity | null;
    identityDocument: Present<Item<LooseDetail["identityDocument"]>, "ocrStatus"> | null;
    riskDecisions: Present<
      Item<LooseDetail["riskDecisions"]>,
      "id" | "decision" | "score" | "threshold" | "modelVersion" | "decidedAt"
    >[] | null;
    rnpn: Present<Item<LooseDetail["rnpn"]>, "outcome" | "simulated" | "attempts" | "checkedAt"> | null;
    timeline: Present<Item<LooseDetail["timeline"]>, "type" | "occurredAt">[] | null;
  };

/** Score model in force: the ranges of each level and the points each signal can add (null: no weight yet). */
export type ScoreModel = {
  version: number;
  name: string;
  low: { from: number; to: number };
  medium: { from: number; to: number };
  high: { from: number; to: number };
  validFrom: string | null;
  weights: { signal: string; name: string; maxPoints: number | null }[];
};

/** A version in the score model history (VDI-120). `publishedBy` is null when a migration published it. */
export type ScoreModelVersion = ScoreModel & {
  validTo: string | null;
  inForce: boolean;
  publishedBy: string | null;
};

export type Alert = {
  id: string;
  account: string;
  reason: string;
  criticality: Criticality;
  status: AlertStatus;
  assigneeId: string | null;
  assigneeName: string | null;
  raisedAt: string;
};

export type CatalogItem = { code: string; label: string; minUsd: number | null; maxUsd: number | null };
export type Catalogs = {
  incomeSources: CatalogItem[];
  incomeRanges: CatalogItem[];
  transactionTypes: CatalogItem[];
  monthlyAmountRanges: CatalogItem[];
};

// ---- Requests

/**
 * API error (application/problem+json body) or network error (status 0). A 400 for invalid fields
 * also lists each field with its message, in Spanish, under `errors`.
 */
export class ApiError extends Error {
  constructor(
    public status: number,
    public title: string,
    public detail?: string,
    public retryAfterSeconds?: number,
    public errors?: Record<string, string>,
  ) {
    super(detail || title);
  }
}

export async function apiFetch<T>(
  path: string,
  { token, method = "GET", body }: { token?: string | null; method?: string; body?: unknown } = {},
): Promise<T> {
  let res: Response;
  try {
    res = await fetch(API_URL + path, {
      method,
      headers: {
        Accept: "application/json",
        ...(body !== undefined && { "Content-Type": "application/json" }),
        ...(token && { Authorization: `Bearer ${token}` }),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(0, "Sin conexión", "No pudimos conectar con el servidor.");
  }
  if (!res.ok) {
    const problem = await res.json().catch(() => ({}));
    const retry = Number(res.headers.get("Retry-After"));
    const errors = problem.errors && typeof problem.errors === "object" ? problem.errors : undefined;
    throw new ApiError(res.status, problem.title ?? res.statusText, problem.detail, retry || undefined, errors);
  }
  return res.status === 204 ? (undefined as T) : res.json();
}

export function login(email: string, password: string) {
  return apiFetch<LoginResponse>("/api/console/auth/login", { method: "POST", body: { email, password } });
}

const MAX_PAGE_SIZE = 100;

/**
 * All requests, walking through the API pages. The console filters by every column,
 * while the API only filters by status, risk and text.
 */
export async function fetchAllRequests(token: string): Promise<RequestSummary[]> {
  const path = (p: number) => `/api/console/requests?page=${p}&size=${MAX_PAGE_SIZE}`;
  const firstPage = await apiFetch<Page<RequestSummary>>(path(0), { token });
  const rest = await Promise.all(
    Array.from({ length: Math.max(firstPage.totalPages - 1, 0) }, (_, i) =>
      apiFetch<Page<RequestSummary>>(path(i + 1), { token }),
    ),
  );
  return [firstPage, ...rest].flatMap((p) => p.content);
}

export type DocumentSide = "FRONT" | "BACK";

/** Decrypted DUI photo of a request, as an image Blob (VDI-80). Every call is recorded in the access audit. */
export async function fetchDocumentPhoto(requestId: string, side: DocumentSide, token: string): Promise<Blob> {
  let res: Response;
  try {
    res = await fetch(`${API_URL}/api/console/requests/${encodeURIComponent(requestId)}/identity-document/${side}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
  } catch {
    throw new ApiError(0, "Sin conexión", "No pudimos conectar con el servidor.");
  }
  if (!res.ok) {
    const problem = await res.json().catch(() => ({}));
    throw new ApiError(res.status, problem.title ?? res.statusText, problem.detail);
  }
  return res.blob();
}
