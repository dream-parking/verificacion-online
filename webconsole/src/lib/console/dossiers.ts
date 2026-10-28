// Search of the archived binding dossier (VDI-136). Contract: docs/openapi.json, GET /api/console/dossiers.

import type { components } from "./openapi";
import { ApiError, apiFetch, type Loose, type RequestDetail } from "./api";

export type DossierSearch = "request" | "customer";

type ArchivedDossierSchema = components["schemas"]["ArchivedDossierResponse"];

/**
 * What the dossier search returns, from the contract (docs/openapi.json). The contract does not mark nullable fields, so
 * the customer number is loose (it comes empty when the request had no numbered customer yet); the request number and
 * the dossier are always there.
 */
export type ArchivedDossier = Omit<Loose<ArchivedDossierSchema>, "requestNumber" | "dossier"> &
  Pick<ArchivedDossierSchema, "requestNumber"> & { dossier: NonNullable<RequestDetail["bindingDossier"]> };

type Rules = { param: "requestNumber" | "customerNumber"; pattern: RegExp; example: string; label: string };

export const SEARCH_RULES: Record<DossierSearch, Rules> = {
  request: { param: "requestNumber", pattern: /^SOL-\d{4}-\d{5,}$/, example: "SOL-2026-00001", label: "Número de solicitud" },
  customer: { param: "customerNumber", pattern: /^CLI-\d{7,}$/, example: "CLI-0000001", label: "Número de cliente" },
};

/** Checks the number the way the API does and returns it trimmed and in capitals, or the message to show. */
export function validateDossierNumber(
  mode: DossierSearch,
  raw: string,
): { value: string; error?: undefined } | { value?: undefined; error: string } {
  const { pattern, example, label } = SEARCH_RULES[mode];
  const value = raw.trim().toUpperCase();
  if (!value) return { error: `Escribe el ${label.toLowerCase()}.` };
  if (value.length > 20 || !pattern.test(value)) return { error: `El ${label.toLowerCase()} tiene el formato ${example}.` };
  return { value };
}

/** Looks the dossier up. A request number gives one dossier and a customer number a list, newest first; both come back as a list. */
export async function findDossiers(token: string, mode: DossierSearch, value: string): Promise<ArchivedDossier[]> {
  const { param } = SEARCH_RULES[mode];
  const found = await apiFetch<ArchivedDossier | ArchivedDossier[]>(
    `/api/console/dossiers?${param}=${encodeURIComponent(value)}`,
    { token },
  );
  return Array.isArray(found) ? found : [found];
}

/** What to tell the person when the search failed. */
export function searchError(e: unknown, mode: DossierSearch): string {
  const status = e instanceof ApiError ? e.status : undefined;
  if (status === 404) {
    return mode === "request"
      ? "No hay una solicitud con ese número, o todavía no firmó el contrato."
      : "No hay un cliente con ese número.";
  }
  if (status === 400) return "El servidor no aceptó el número. Revisa el formato.";
  if (status === 403) return "Tu rol no puede consultar expedientes.";
  if (status === 0) return "No pudimos conectar con el servidor. Revisa tu conexión e intenta de nuevo.";
  return "No pudimos consultar el expediente. Intenta de nuevo.";
}
