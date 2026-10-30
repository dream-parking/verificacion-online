// Outcome of a case: confirmed fraud or false positive (VDI-145). Contract: docs/openapi.json,
// /api/console/requests/{requestId}/outcome. An outcome is never edited: correcting it registers a new one.

import type { components } from "./openapi";
import { ApiError, apiFetch } from "./api";

export type Outcome = components["schemas"]["RequestOutcomeResponse"];
export type OutcomeKind = components["schemas"]["RegisterOutcomeRequest"]["outcome"];
export type ListedData = Outcome["listed"][number];

export const OUTCOME_KINDS: OutcomeKind[] = ["FRAUDE_CONFIRMADO", "FALSO_POSITIVO"];

/** How each outcome reads, with the badge that tells them apart at a glance (red for fraud, green for false positive). */
export const OUTCOME: Record<OutcomeKind, { cls: string; label: string; action: string; correction: string }> = {
  FRAUDE_CONFIRMADO: {
    cls: "s-abandoned",
    label: "▲ Fraude confirmado",
    action: "Confirmar fraude",
    correction: "Corregir a fraude confirmado",
  },
  FALSO_POSITIVO: {
    cls: "s-ok",
    label: "✓ Falso positivo",
    action: "Marcar como falso positivo",
    correction: "Corregir a falso positivo",
  },
};

export const LISTED: Record<ListedData, string> = { DOCUMENT: "el DUI", PHONE: "el teléfono", DEVICE: "el dispositivo" };

/** "el DUI, el teléfono y el dispositivo" */
export function listedText(data: readonly ListedData[]): string {
  const names = data.map((d) => LISTED[d] ?? d.toLowerCase());
  if (names.length <= 1) return names.join("");
  return `${names.slice(0, -1).join(", ")} y ${names[names.length - 1]}`;
}

export const REASON_MAX = 150;
const ALLOWED = /^[\p{L}\p{N}\p{Sc} .,;:¿?¡!'"‘’“”«»()/&%#@+*_°–—…-]*$/u;

/** Checks the reason the way the API does: required, up to 150 characters, with a letter or number and common punctuation. */
export function validateReason(raw: string): { value: string; error?: undefined } | { value?: undefined; error: string } {
  const value = raw.trim();
  if (!value) return { error: "Escribe por qué se cierra así el caso." };
  if (value.length > REASON_MAX) return { error: `Usa ${REASON_MAX} caracteres o menos.` };
  if (!ALLOWED.test(value) || !/[\p{L}\p{N}]/u.test(value)) {
    return { error: "Usa letras, números y signos de puntuación comunes." };
  }
  return { value };
}

const path = (requestId: string) => `/api/console/requests/${encodeURIComponent(requestId)}/outcome`;

/** The outcomes of a request, oldest first: the last one is the one in force. */
export function fetchOutcomes(token: string, requestId: string) {
  return apiFetch<Outcome[]>(path(requestId), { token });
}

export function registerOutcome(token: string, requestId: string, outcome: OutcomeKind, reason: string) {
  return apiFetch<Outcome>(path(requestId), { token, method: "POST", body: { outcome, reason } });
}

/** What to tell the person when registering failed: the message the API sent with a 400 (already in Spanish) or a general one. */
export function outcomeError(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.status === 400) return e.errors?.reason ?? e.errors?.outcome ?? "El servidor no aceptó el desenlace. Revisa el motivo.";
    if (e.status === 403) return "Solo los analistas de fraude y los administradores pueden registrar el desenlace.";
    if (e.status === 404) return "Esta solicitud ya no existe.";
    if (e.status === 0) return "No pudimos conectar con el servidor. Revisa tu conexión e intenta de nuevo.";
  }
  return "No pudimos registrar el desenlace. Intenta de nuevo.";
}
