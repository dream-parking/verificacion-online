// Labels and formats the console shows from the API data.

import type { Criticality, AlertStatus, RequestStatus, RiskLevel, Step, ScoreRule, Role } from "./api";

export type Badge = { cls: string; label: string };

export const RISK: Record<RiskLevel, Badge> = {
  NOT_EVALUATED: { cls: "b-none", label: "○ Sin evaluar" },
  LOW: { cls: "b-low", label: "● Bajo" },
  PENDING_REVIEW: { cls: "b-pending", label: "◌ Pendiente de evaluación" },
  MEDIUM: { cls: "c-medium", label: "■ Medio" },
  HIGH: { cls: "c-high", label: "▲ Alto" },
};

export const REQUEST_STATUS: Record<RequestStatus, Badge> = {
  COMPLETED: { cls: "s-ok", label: "✓ Completada" },
  IN_PROGRESS: { cls: "s-progress", label: "… En progreso" },
  ABANDONED: { cls: "s-abandoned", label: "✕ Abandonada" },
};

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
  { step: "INCOME", name: "Ingresos" },
  { step: "EXPECTED_ACTIVITY", name: "Movimiento esperado" },
  { step: "REVIEW", name: "Revisión" },
];

export const RULE_STATUS: Record<ScoreRule["status"], string> = {
  DRAFT: "Borrador",
  PROVISIONAL: "Provisional",
  CONFIRMED: "Confirmada",
  RETIRED: "Retirada",
};

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
