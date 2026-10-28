// Tolerance of the deviation from the declared profile (VDI-138, ADMIN only).
// Contract: docs/openapi.json, /api/console/admin/profile-tolerance.

import { ApiError, apiFetch } from "./api";

export type ProfileToleranceChange = {
  changedAt: string;
  /** Console user who made the change; null in the initial value. */
  changedBy: string | null;
  /** Null in the initial value. */
  previousPercent: number | null;
  tolerancePercent: number;
};

export type ProfileTolerance = {
  tolerancePercent: number;
  minPercent: number;
  maxPercent: number;
  /** Newest first. */
  changes: ProfileToleranceChange[];
};

export function fetchProfileTolerance(token: string) {
  return apiFetch<ProfileTolerance>("/api/console/admin/profile-tolerance", { token });
}

export function changeProfileTolerance(token: string, tolerancePercent: number) {
  return apiFetch<ProfileTolerance>("/api/console/admin/profile-tolerance", {
    token,
    method: "PUT",
    body: { tolerancePercent },
  });
}

/**
 * Checks the tolerance the way the API does: a whole number between the minimum and the maximum, different
 * from the one in force. The API validates it again.
 */
export function validateTolerance(
  raw: string,
  { tolerancePercent, minPercent, maxPercent }: Pick<ProfileTolerance, "tolerancePercent" | "minPercent" | "maxPercent">,
): { value: number; error?: undefined } | { value?: undefined; error: string } {
  const text = raw.trim();
  if (!text) return { error: "Escribe la tolerancia." };
  if (!/^\d+$/.test(text)) return { error: "Usa un número entero, sin decimales ni signo." };
  const value = Number(text);
  if (value < minPercent || value > maxPercent) {
    return { error: `Debe estar entre ${minPercent} y ${maxPercent}.` };
  }
  if (value === tolerancePercent) return { error: "Es la misma tolerancia que está vigente. Escribe otra para cambiarla." };
  return { value };
}

/** Message to show when the change failed: the one the API sent with a 400 (already in Spanish) or a general one. */
export function changeError(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.status === 400) {
      return e.errors?.tolerancePercent ?? "El servidor no aceptó la tolerancia. Revisa el valor.";
    }
    if (e.status === 403) return "Tu rol no puede cambiar la tolerancia. Solo un administrador puede hacerlo.";
    if (e.status === 0) return "No pudimos conectar con el servidor. Revisa tu conexión e intenta de nuevo.";
  }
  return "No pudimos cambiar la tolerancia. Intenta de nuevo.";
}
