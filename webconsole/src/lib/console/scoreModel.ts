// Score thresholds (VDI-120, ADMIN only). Contract: docs/openapi.json, /admin/score-model/versions.

import { ApiError, apiFetch, type ScoreModel, type ScoreModelVersion } from "./api";

export type Thresholds = { lowMax: number; mediumMax: number };
export type ThresholdField = keyof Thresholds;
export type ThresholdErrors = Partial<Record<ThresholdField | "form", string>>;

const LABEL: Record<ThresholdField, string> = { lowMax: "el fin del nivel Bajo", mediumMax: "el fin del nivel Medio" };

function parse(field: ThresholdField, raw: string, errors: ThresholdErrors): number | null {
  const text = raw.trim();
  if (!text) {
    errors[field] = `Escribe ${LABEL[field]}.`;
    return null;
  }
  if (!/^-?\d+$/.test(text)) {
    errors[field] = "Usa un número entero, sin decimales.";
    return null;
  }
  return Number(text);
}

/**
 * Checks the thresholds the way the API does: whole numbers with 0 ≤ lowMax < mediumMax < 100,
 * different from the version in force. Returns the values when they are valid, or the message of
 * each field (and `form` when nothing changes). The API validates them again.
 */
export function validateThresholds(
  lowMax: string,
  mediumMax: string,
  current?: Thresholds,
): { values: Thresholds; errors?: undefined } | { values?: undefined; errors: ThresholdErrors } {
  const errors: ThresholdErrors = {};
  const low = parse("lowMax", lowMax, errors);
  const medium = parse("mediumMax", mediumMax, errors);

  if (low !== null && low < 0) errors.lowMax = "No puede ser menor que 0.";
  if (medium !== null && medium >= 100) errors.mediumMax = "Debe ser menor que 100.";
  if (low !== null && medium !== null && !errors.lowMax && !errors.mediumMax && low >= medium) {
    errors.lowMax = "Debe ser menor que el fin del nivel Medio.";
  }
  if (Object.keys(errors).length || low === null || medium === null) return { errors };

  if (current && current.lowMax === low && current.mediumMax === medium) {
    return { errors: { form: "Son los mismos umbrales de la versión vigente. Cambia al menos uno para publicar." } };
  }
  return { values: { lowMax: low, mediumMax: medium } };
}

/** Where each level starts and ends with these thresholds. */
export function rangesOf({ lowMax, mediumMax }: Thresholds) {
  return {
    low: { from: 0, to: lowMax },
    medium: { from: lowMax + 1, to: mediumMax },
    high: { from: mediumMax + 1, to: 100 },
  };
}

export const thresholdsOf = (model: ScoreModel): Thresholds => ({ lowMax: model.low.to, mediumMax: model.medium.to });

export function fetchScoreModelVersions(token: string) {
  return apiFetch<ScoreModelVersion[]>("/api/console/admin/score-model/versions", { token });
}

export function publishScoreModelVersion(token: string, thresholds: Thresholds) {
  return apiFetch<ScoreModelVersion>("/api/console/admin/score-model/versions", {
    token,
    method: "POST",
    body: thresholds,
  });
}

/** Field messages the API sent with a 400 (already in Spanish), or the general message to show. */
export function publishError(e: unknown): ThresholdErrors {
  if (e instanceof ApiError) {
    if (e.status === 400 && e.errors && (e.errors.lowMax || e.errors.mediumMax)) {
      return { lowMax: e.errors.lowMax, mediumMax: e.errors.mediumMax };
    }
    if (e.status === 400) return { form: "El servidor no aceptó los umbrales. Revisa los dos valores." };
    if (e.status === 403) return { form: "Tu rol no puede cambiar los umbrales. Solo un administrador puede hacerlo." };
    if (e.status === 409) {
      return { form: "El modelo vigente cambió mientras editabas. Actualiza la página y vuelve a intentarlo." };
    }
    if (e.status === 0) return { form: "No pudimos conectar con el servidor. Revisa tu conexión e intenta de nuevo." };
  }
  return { form: "No pudimos publicar los umbrales. Intenta de nuevo." };
}
