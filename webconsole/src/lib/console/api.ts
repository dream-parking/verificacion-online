// Client of the console API. The types follow docs/openapi.json; in responses the optional
// fields can come as null (see docs/integracion-api.md).

const URLS_BY_ENV: Record<string, string> = {
  dev: "https://api.dev.identidad.alambritos.online",
  qa: "https://api.qa.identidad.alambritos.online",
};

/** NEXT_PUBLIC_API_URL has priority; otherwise it is derived from the build environment (local by default). */
export const API_URL =
  process.env.NEXT_PUBLIC_API_URL ||
  URLS_BY_ENV[process.env.NEXT_PUBLIC_APP_ENV ?? ""] ||
  "http://localhost:8080";

// ---- Contract types

export type Role = "KYC_LEAD" | "FRAUD_ANALYST" | "ADMIN";
export type RiskLevel = "NOT_EVALUATED" | "LOW" | "PENDING_REVIEW" | "MEDIUM" | "HIGH";
export type RequestStatus = "IN_PROGRESS" | "COMPLETED" | "ABANDONED";
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
  status: RequestStatus;
  completedSteps: number;
};

export type Assessment = {
  level: RiskLevel;
  ruleCode: string | null;
  ruleName: string | null;
  ruleThreshold: number | null;
  evaluatedValue: number | null;
  explanation: string | null;
  evaluatedAt: string | null;
};

/** What the IP geolocation provider returned. When the location is UNAVAILABLE only `failure` and `lookedUpAt` are set. */
export type IpDetails = {
  country: string | null;
  countryCode: string | null;
  region: string | null;
  regionName: string | null;
  city: string | null;
  zip: string | null;
  timezone: string | null;
  isp: string | null;
  org: string | null;
  asName: string | null;
  failure: string | null;
  lookedUpAt: string | null;
};

export type RequestDetail = {
  id: string;
  number: string | null;
  status: RequestStatus;
  riskLevel: RiskLevel;
  completedSteps: number;
  startedAt: string;
  submittedAt: string | null;
  applicant: { firstNames: string | null; lastNames: string | null; dui: string | null; mobilePhone: string | null } | null;
  income: {
    sourceCode: string;
    sourceLabel: string | null;
    sourceDetail: string | null;
    rangeCode: string;
    rangeLabel: string | null;
    registeredAt: string;
    updatedAt: string | null;
  } | null;
  expectedActivity: {
    transactionTypeCode: string;
    transactionTypeLabel: string | null;
    monthlyAmountRangeCode: string;
    monthlyAmountRangeLabel: string | null;
    registeredAt: string;
    updatedAt: string | null;
  } | null;
  risk: Assessment | null;
  signals: {
    ip: string | null;
    approximateLocation: string | null;
    /** Whether the server could locate the IP (VDI-67). Missing in responses of older API versions. */
    locationStatus?: "AVAILABLE" | "UNAVAILABLE" | null;
    latitude?: number | null;
    longitude?: number | null;
    ipDetails?: IpDetails | null;
    /** When the app last sent the signals (VDI-70). Missing in responses of older API versions. */
    capturedAt?: string | null;
    deviceFingerprint: string | null;
    device: string | null;
    nightTime: boolean | null;
    typingPace: "SLOW" | "NORMAL" | "FAST" | null;
    typingSpeedCpm: number | null;
    totalDurationSeconds: number | null;
    requestsFromSameDevice: number | null;
  } | null;
  steps:
    | {
        step: Step;
        startedAt: string | null;
        completedAt: string | null;
        durationSeconds: number | null;
        attempts: number | null;
        /** Typing speed on that screen, in characters per second (omitted when nothing was typed). */
        typingSpeedCps?: number | null;
      }[]
    | null;
  timeline: { type: string; description: string | null; actor: string | null; occurredAt: string }[] | null;
};

export type ScoreRule = {
  code: string;
  name: string;
  description: string | null;
  evaluatedField: string | null;
  operator: string | null;
  threshold: number | null;
  currency: string | null;
  resultIfMatched: RiskLevel;
  resultIfNotMatched: RiskLevel | null;
  status: "DRAFT" | "PROVISIONAL" | "CONFIRMED" | "RETIRED";
  statusNote: string | null;
  validFrom: string | null;
  version: number | null;
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

/** API error (application/problem+json body) or network error (status 0). */
export class ApiError extends Error {
  constructor(
    public status: number,
    public title: string,
    public detail?: string,
    public retryAfterSeconds?: number,
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
    throw new ApiError(res.status, problem.title ?? res.statusText, problem.detail, retry || undefined);
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
