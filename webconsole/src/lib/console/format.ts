// Labels and formats the console shows from the API data.

import type {
  Criticality,
  AlertStatus,
  DeviceLocationStatus,
  EscalationChannel,
  ProfileVersion,
  RequestStatus,
  RiskLevel,
  Step,
  Role,
} from "./api";

export type Badge = { cls: string; label: string };

export const RISK: Record<RiskLevel, Badge> = {
  NOT_EVALUATED: { cls: "b-none", label: "○ Sin evaluar" },
  LOW: { cls: "b-low", label: "● Bajo" },
  PENDING_REVIEW: { cls: "b-pending", label: "◌ Pendiente de evaluación" },
  MEDIUM: { cls: "c-medium", label: "■ Medio" },
  HIGH: { cls: "c-high", label: "▲ Alto" },
};

export const REQUEST_STATUS: Record<RequestStatus, Badge> = {
  IN_PROGRESS: { cls: "s-progress", label: "… En progreso" },
  COMPLETED: { cls: "s-ok", label: "✓ Completada" },
  ENHANCED_REVIEW: { cls: "s-enhanced", label: "⚑ Revisión reforzada" },
  AWAITING_RNPN: { cls: "s-waiting", label: "◷ En espera" },
  ESCALATED: { cls: "s-enhanced", label: "⇪ Escalada" },
  ABANDONED: { cls: "s-abandoned", label: "✕ Abandonada" },
};

/** Options of the status filter, in the order of REQUEST_STATUS and without the symbol that precedes each badge label. */
export const REQUEST_STATUS_OPTIONS = Object.entries(REQUEST_STATUS).map(([value, { label }]) => ({
  value,
  label: label.replace(/^\S+\s/, ""),
}));

/** Where an analyst escalated a request (VDI-143). */
export const ESCALATION_CHANNEL: Record<EscalationChannel, string> = {
  VIDEO_CALL: "Videollamada",
  BRANCH: "Agencia",
};

/**
 * The person already sent the request: approved automatically, held for enhanced review, escalated or waiting for the
 * RNPN.
 */
export const isSubmitted = (status: RequestStatus) =>
  status === "COMPLETED" || status === "ENHANCED_REVIEW" || status === "AWAITING_RNPN" || status === "ESCALATED";

export const CRITICALITY: Record<Criticality, Badge> = {
  CRITICAL: { cls: "c-critical", label: "◆ Crítica" },
  HIGH: { cls: "c-high", label: "▲ Alta" },
  MEDIUM: { cls: "c-medium", label: "■ Media" },
  LOW: { cls: "c-low", label: "● Baja" },
};

export const ALERT_STATUS: Record<AlertStatus, Badge> = {
  UNASSIGNED: { cls: "s-unassigned", label: "○ Sin asignar" },
  ASSIGNED: { cls: "s-assigned", label: "◐ Asignada" },
  IN_REVIEW: { cls: "s-review", label: "◉ En revisión" },
  CLOSED: { cls: "s-ok", label: "✓ Cerrada" },
};

export const ROLE_LABEL: Record<Role, string> = {
  KYC_LEAD: "Conozca a su Cliente",
  FRAUD_ANALYST: "Fraude y cumplimiento",
  ADMIN: "Administración",
};

/** Roles that can take alerts (POST /alerts/{id}/take). */
export const canTakeAlerts = (role: Role) => role === "FRAUD_ANALYST" || role === "ADMIN";

export const STEPS: { step: Step; name: string }[] = [
  { step: "PRIVACY_NOTICE", name: "Aviso de privacidad" },
  { step: "BASIC_DATA", name: "Datos básicos" },
  { step: "IDENTITY_DOCUMENT", name: "DUI" },
  { step: "INCOME", name: "Ingresos" },
  { step: "EXPECTED_ACTIVITY", name: "Movimiento esperado" },
  { step: "REVIEW", name: "Revisión" },
];

export const TYPING_PACE: Record<"SLOW" | "NORMAL" | "FAST", string> = {
  SLOW: "Pausado",
  NORMAL: "Normal",
  FAST: "Más rápido que el promedio",
};

/** Why the server could not locate an IP, in words for the analyst (the API sends a short English reason). */
export function locationFailureText(failure: string | null | undefined): string {
  const reason = (failure ?? "").toLowerCase();
  if (reason.includes("private") || reason.includes("reserved")) {
    return "La dirección IP es privada o reservada (por ejemplo, una red local) y ningún proveedor puede ubicarla.";
  }
  if (reason.includes("disabled")) return "La consulta de ubicación está desactivada en el servidor.";
  if (reason.includes("provider")) {
    return "El proveedor de ubicación no respondió o alcanzó su límite de consultas. Se puede reintentar más tarde.";
  }
  return "No se pudo ubicar esta dirección IP.";
}

/**
 * "Ciudad de la solicitud" (VDI-73): the city the phone reported. Without permission, or when the phone could not
 * find it, the console shows «Ubicación no disponible» and the request goes on.
 */
export function deviceLocationText(
  status: DeviceLocationStatus | null | undefined,
  location: string | null | undefined,
): string {
  switch (status) {
    case "AVAILABLE":
      return location || "Ubicación sin nombre";
    case "DENIED":
      return "Ubicación no disponible · la persona no dio permiso";
    case "UNAVAILABLE":
      return "Ubicación no disponible · el teléfono no pudo obtenerla";
    default:
      return "Sin dato · la app no la envió";
  }
}

// ---- Dates: always in El Salvador time (UTC−6, no daylight saving) and in 12-hour format

const TIMEZONE = "America/El_Salvador";
export const TIME_NOTE = "Fechas y horas en hora de El Salvador.";

const dateFormatter = new Intl.DateTimeFormat("en-US", {
  timeZone: TIMEZONE,
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  hour: "numeric",
  minute: "2-digit",
  second: "2-digit",
  hour12: true,
});

function parts(iso: string) {
  const p = Object.fromEntries(dateFormatter.formatToParts(new Date(iso)).map((x) => [x.type, x.value]));
  return {
    ...(p as Record<"day" | "month" | "year" | "hour" | "minute" | "second", string>),
    ampm: p.dayPeriod === "PM" ? "p. m." : "a. m.",
  };
}

/** 05/10/2026 */
export const formatDate = (iso: string) => {
  const p = parts(iso);
  return `${p.day}/${p.month}/${p.year}`;
};
/** 10:08 p. m. */
export const formatTime = (iso: string) => {
  const p = parts(iso);
  return `${p.hour}:${p.minute} ${p.ampm}`;
};
/** 05/10 10:08 p. m. */
export const formatShortDate = (iso: string) => {
  const p = parts(iso);
  return `${p.day}/${p.month} ${p.hour}:${p.minute} ${p.ampm}`;
};
/** 05/10/2026 10:08:03 p. m. */
export const formatDateTime = (iso: string) => {
  const p = parts(iso);
  return `${p.day}/${p.month}/${p.year} ${p.hour}:${p.minute}:${p.second} ${p.ampm}`;
};

const pad2 = (n: number) => String(n).padStart(2, "0");
/** 1:08 */
export const minSec = (s: number) => `${Math.floor(s / 60)}:${pad2(s % 60)}`;
/** 5 min 08 s */
export const duration = (s: number) => `${Math.floor(s / 60)} min ${pad2(s % 60)} s`;

export const usd = (n: number, currency = "USD") => `${currency} ${n.toLocaleString("en-US")}`;

/** What each way of straying from the declared profile means (VDI-138), as the alert evidence names it. */
export const PROFILE_DEVIATION: Record<string, { label: string; unit: "usd" | "count" }> = {
  INFLOW_ABOVE_RANGE: { label: "Ingresos por encima del rango mensual declarado", unit: "usd" },
  INFLOW_ABOVE_DECLARED_AMOUNT: { label: "Ingresos por encima del monto mensual declarado", unit: "usd" },
  MORE_TRANSACTIONS_THAN_DECLARED: { label: "Más movimientos de entrada que los declarados", unit: "count" },
  OUTFLOW_ABOVE_DECLARED: { label: "Salidas por encima de lo declarado", unit: "usd" },
  UNDECLARED_TRANSACTION_TYPE: { label: "Dinero recibido de un tipo que no declaró", unit: "usd" },
};

/** Alert types raised when the activity strays from the declared profile (VDI-138), and the aspect each one is about. */
export const PROFILE_ALERT_TYPE: Record<string, string> = {
  PERFIL_EXCEDIDO: "Movimientos por encima de lo declarado en el perfil",
  ORIGEN_DISTINTO: "Dinero recibido de un tipo que no declaró",
};

/** What the declared profile card says when the person has not declared the expected movement yet. */
export const NO_PROFILE = "Sin declaración de movimiento";

export type ProfileEntry = {
  version: number;
  /** Only the newest version is the one in force, whatever the API says for the others. */
  current: boolean;
  /** "09/10/2026 · 9:51 a. m.", plus who changed it from version 2 on. */
  when: string;
  transactionType: string;
  monthlyAmount: string;
};

/** The versions of the declared profile for the console: newest first (the API sends them the other way around). */
export function profileTimeline(versions: ProfileVersion[] | null | undefined): ProfileEntry[] {
  const newest = Math.max(0, ...(versions ?? []).map((v) => v.version));
  return [...(versions ?? [])]
    .sort((a, b) => b.version - a.version)
    .map((v) => ({
      version: v.version,
      current: v.version === newest,
      when: `${formatDate(v.declaredAt)} · ${formatTime(v.declaredAt)}${
        v.version >= 2 && v.changedBy === "CUSTOMER" ? " · cambiada por la persona antes de enviar" : ""
      }`,
      transactionType: v.transactionTypeLabel ?? v.transactionTypeCode,
      monthlyAmount: v.monthlyAmountRangeLabel ?? v.monthlyAmountRangeCode,
    }));
}
