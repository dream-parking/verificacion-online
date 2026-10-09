// Cliente del API de la consola. Los tipos siguen docs/openapi.json; en las
// respuestas los campos opcionales pueden venir null (ver docs/integracion-api.md).

const URLS_POR_AMBIENTE: Record<string, string> = {
  dev: "https://api.dev.identidad.alambritos.online",
  qa: "https://api.qa.identidad.alambritos.online",
};

/** NEXT_PUBLIC_API_URL tiene prioridad; si no, se deduce del ambiente del build (local por defecto). */
export const API_URL =
  process.env.NEXT_PUBLIC_API_URL ||
  URLS_POR_AMBIENTE[process.env.NEXT_PUBLIC_APP_ENV ?? ""] ||
  "http://localhost:8080";

// ---- Tipos del contrato

export type Rol = "KYC_LEAD" | "FRAUD_ANALYST" | "ADMIN";
export type NivelRiesgo = "NOT_EVALUATED" | "LOW" | "PENDING_REVIEW" | "MEDIUM" | "HIGH";
export type EstadoSolicitud = "IN_PROGRESS" | "COMPLETED" | "ABANDONED";
export type Criticidad = "CRITICAL" | "HIGH" | "MEDIUM" | "LOW";
export type EstadoAlerta = "UNASSIGNED" | "ASSIGNED" | "IN_REVIEW" | "CLOSED";
export type Paso = "PRIVACY_NOTICE" | "IDENTITY_DOCUMENT" | "BASIC_DATA" | "INCOME" | "EXPECTED_ACTIVITY" | "REVIEW";

export type UsuarioConsola = {
  id: string;
  email: string;
  fullName: string;
  initials: string | null;
  jobTitle: string | null;
  role: Rol;
  active: boolean;
};

export type LoginResponse = {
  accessToken: string;
  expiresIn: number;
  tokenType: string;
  user: UsuarioConsola;
};

export type Pagina<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type SolicitudResumen = {
  id: string;
  number: string | null;
  name: string | null;
  date: string;
  transactionTypeLabel: string | null;
  monthlyAmountRangeLabel: string | null;
  riskLevel: NivelRiesgo;
  status: EstadoSolicitud;
  completedSteps: number;
};

export type Evaluacion = {
  level: NivelRiesgo;
  ruleCode: string | null;
  ruleName: string | null;
  ruleThreshold: number | null;
  evaluatedValue: number | null;
  explanation: string | null;
  evaluatedAt: string | null;
};

export type SolicitudDetalle = {
  id: string;
  number: string | null;
  status: EstadoSolicitud;
  riskLevel: NivelRiesgo;
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
  risk: Evaluacion | null;
  signals: {
    ip: string | null;
    approximateLocation: string | null;
    deviceFingerprint: string | null;
    device: string | null;
    nightTime: boolean | null;
    typingPace: "SLOW" | "NORMAL" | "FAST" | null;
    typingSpeedCpm: number | null;
    totalDurationSeconds: number | null;
    requestsFromSameDevice: number | null;
  } | null;
  steps: { step: Paso; startedAt: string | null; completedAt: string | null; durationSeconds: number | null; attempts: number | null }[] | null;
  timeline: { type: string; description: string | null; actor: string | null; occurredAt: string }[] | null;
};

export type ReglaScore = {
  code: string;
  name: string;
  description: string | null;
  evaluatedField: string | null;
  operator: string | null;
  threshold: number | null;
  currency: string | null;
  resultIfMatched: NivelRiesgo;
  resultIfNotMatched: NivelRiesgo | null;
  status: "DRAFT" | "PROVISIONAL" | "CONFIRMED" | "RETIRED";
  statusNote: string | null;
  validFrom: string | null;
  version: number | null;
};

export type Alerta = {
  id: string;
  account: string;
  reason: string;
  criticality: Criticidad;
  status: EstadoAlerta;
  assigneeId: string | null;
  assigneeName: string | null;
  raisedAt: string;
};

export type ItemCatalogo = { code: string; label: string; minUsd: number | null; maxUsd: number | null };
export type Catalogos = {
  incomeSources: ItemCatalogo[];
  incomeRanges: ItemCatalogo[];
  transactionTypes: ItemCatalogo[];
  monthlyAmountRanges: ItemCatalogo[];
};

// ---- Peticiones

/** Error del API (cuerpo application/problem+json) o de red (status 0). */
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
  ruta: string,
  { token, method = "GET", body }: { token?: string | null; method?: string; body?: unknown } = {},
): Promise<T> {
  let res: Response;
  try {
    res = await fetch(API_URL + ruta, {
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
    const problema = await res.json().catch(() => ({}));
    const retry = Number(res.headers.get("Retry-After"));
    throw new ApiError(res.status, problema.title ?? res.statusText, problema.detail, retry || undefined);
  }
  return res.status === 204 ? (undefined as T) : res.json();
}

export function login(email: string, password: string) {
  return apiFetch<LoginResponse>("/api/console/auth/login", { method: "POST", body: { email, password } });
}

const TAMANO_MAXIMO = 100;

/**
 * Todas las solicitudes, recorriendo las páginas del API. La consola filtra por
 * todas las columnas y el API solo filtra por estado, riesgo y texto.
 */
export async function todasLasSolicitudes(token: string): Promise<SolicitudResumen[]> {
  const ruta = (p: number) => `/api/console/requests?page=${p}&size=${TAMANO_MAXIMO}`;
  const primera = await apiFetch<Pagina<SolicitudResumen>>(ruta(0), { token });
  const resto = await Promise.all(
    Array.from({ length: Math.max(primera.totalPages - 1, 0) }, (_, i) =>
      apiFetch<Pagina<SolicitudResumen>>(ruta(i + 1), { token }),
    ),
  );
  return [primera, ...resto].flatMap((p) => p.content);
}
