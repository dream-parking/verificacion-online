// Etiquetas y formatos que muestra la consola a partir de los datos del API.

import type { Criticidad, EstadoAlerta, EstadoSolicitud, NivelRiesgo, Paso, ReglaScore, Rol } from "./api";

export type Insignia = { cls: string; label: string };

export const RIESGO: Record<NivelRiesgo, Insignia> = {
  NOT_EVALUATED: { cls: "b-sin", label: "○ Sin evaluar" },
  LOW: { cls: "b-bajo", label: "● Bajo" },
  PENDING_REVIEW: { cls: "b-pend", label: "◌ Pendiente de evaluación" },
  MEDIUM: { cls: "c-media", label: "■ Medio" },
  HIGH: { cls: "c-alta", label: "▲ Alto" },
};

export const ESTADO_SOLICITUD: Record<EstadoSolicitud, Insignia> = {
  COMPLETED: { cls: "s-ok", label: "✓ Completada" },
  IN_PROGRESS: { cls: "s-prog", label: "… En progreso" },
  ABANDONED: { cls: "s-aband", label: "✕ Abandonada" },
};

export const CRITICIDAD: Record<Criticidad, Insignia> = {
  CRITICAL: { cls: "c-crit", label: "◆ Crítica" },
  HIGH: { cls: "c-alta", label: "▲ Alta" },
  MEDIUM: { cls: "c-media", label: "■ Media" },
  LOW: { cls: "c-baja", label: "● Baja" },
};

export const ESTADO_ALERTA: Record<EstadoAlerta, Insignia> = {
  UNASSIGNED: { cls: "s-sin", label: "○ Sin asignar" },
  ASSIGNED: { cls: "s-asig", label: "◐ Asignada" },
  IN_REVIEW: { cls: "s-rev", label: "◉ En revisión" },
  CLOSED: { cls: "s-ok", label: "✓ Cerrada" },
};

export const ROL: Record<Rol, string> = {
  KYC_LEAD: "Conozca a su Cliente",
  FRAUD_ANALYST: "Fraude y cumplimiento",
  ADMIN: "Administración",
};

/** Roles que pueden tomar alertas (POST /alerts/{id}/take). */
export const puedeTomarAlertas = (rol: Rol) => rol === "FRAUD_ANALYST" || rol === "ADMIN";

export const PASOS: { paso: Paso; nombre: string }[] = [
  { paso: "PRIVACY_NOTICE", nombre: "Aviso de privacidad" },
  { paso: "BASIC_DATA", nombre: "Datos básicos" },
  { paso: "INCOME", nombre: "Ingresos" },
  { paso: "EXPECTED_ACTIVITY", nombre: "Movimiento esperado" },
  { paso: "REVIEW", nombre: "Revisión" },
];

export const ESTADO_REGLA: Record<ReglaScore["status"], string> = {
  DRAFT: "Borrador",
  PROVISIONAL: "Provisional",
  CONFIRMED: "Confirmada",
  RETIRED: "Retirada",
};

export const RITMO: Record<"SLOW" | "NORMAL" | "FAST", string> = {
  SLOW: "Pausado",
  NORMAL: "Normal",
  FAST: "Más rápido que el promedio",
};

// ---- Fechas: siempre en hora de El Salvador (UTC−6, sin horario de verano) y en formato de 12 horas

const ZONA = "America/El_Salvador";
export const NOTA_HORA = "Fechas y horas en hora de El Salvador.";

const formato = new Intl.DateTimeFormat("en-US", {
  timeZone: ZONA,
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  hour: "numeric",
  minute: "2-digit",
  second: "2-digit",
  hour12: true,
});

function partes(iso: string) {
  const p = Object.fromEntries(formato.formatToParts(new Date(iso)).map((x) => [x.type, x.value]));
  return {
    ...(p as Record<"day" | "month" | "year" | "hour" | "minute" | "second", string>),
    ampm: p.dayPeriod === "PM" ? "p. m." : "a. m.",
  };
}

/** 05/10/2026 */
export const fecha = (iso: string) => {
  const p = partes(iso);
  return `${p.day}/${p.month}/${p.year}`;
};
/** 10:08 p. m. */
export const hora = (iso: string) => {
  const p = partes(iso);
  return `${p.hour}:${p.minute} ${p.ampm}`;
};
/** 05/10 10:08 p. m. */
export const fechaCorta = (iso: string) => {
  const p = partes(iso);
  return `${p.day}/${p.month} ${p.hour}:${p.minute} ${p.ampm}`;
};
/** 05/10/2026 10:08:03 p. m. */
export const fechaHora = (iso: string) => {
  const p = partes(iso);
  return `${p.day}/${p.month}/${p.year} ${p.hour}:${p.minute}:${p.second} ${p.ampm}`;
};

const p2 = (n: number) => String(n).padStart(2, "0");
/** 1:08 */
export const minSeg = (s: number) => `${Math.floor(s / 60)}:${p2(s % 60)}`;
/** 5 min 08 s */
export const duracion = (s: number) => `${Math.floor(s / 60)} min ${p2(s % 60)} s`;

export const usd = (n: number, moneda = "USD") => `${moneda} ${n.toLocaleString("en-US")}`;
