// What the console shows of the DUI the person confirmed (VDI-81): what was read from the photo next to what
// they confirmed, and which fields they changed.

import type { DuiData, IdentityDocument } from "./api";

export type DuiField = "dui" | "firstNames" | "lastNames" | "birthDate" | "expiryDate";

export type DuiRow = {
  field: DuiField;
  label: string;
  read: string;
  confirmed: string;
  /** The person changed what the reading said (the API lists these in `correctedFields`). */
  corrected: boolean;
};

const FIELDS: { field: DuiField; label: string }[] = [
  { field: "dui", label: "Número de DUI" },
  { field: "firstNames", label: "Nombres" },
  { field: "lastNames", label: "Apellidos" },
  { field: "birthDate", label: "Fecha de nacimiento" },
  { field: "expiryDate", label: "Fecha de vencimiento" },
];

const NO_VALUE = "—";

/** "2026-10-14" as "14/10/2026". Plain dates have no time zone, so they are not converted. */
function plainDate(value: string): string {
  const [y, m, d] = value.split("-");
  return y && m && d ? `${d}/${m}/${y}` : value;
}

function show(field: DuiField, data: DuiData | null | undefined): string {
  const value = data?.[field];
  if (!value) return NO_VALUE;
  return field === "birthDate" || field === "expiryDate" ? plainDate(value) : value;
}

/** One row per field, "read" against "confirmed". A field the person has not confirmed yet shows a dash. */
export function duiRows(doc: Pick<IdentityDocument, "read" | "confirmed" | "correctedFields">): DuiRow[] {
  const corrected = new Set(doc.correctedFields ?? []);
  return FIELDS.map(({ field, label }) => ({
    field,
    label,
    read: show(field, doc.read),
    confirmed: show(field, doc.confirmed),
    corrected: corrected.has(field),
  }));
}
