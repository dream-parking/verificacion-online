"use client";

import type { RnpnCheck, RnpnField, RnpnOutcome } from "@/lib/console/api";
import { formatDateTime } from "@/lib/console/format";
import { KeyValue, type Pair } from "./Ui";

const OUTCOME: Record<RnpnOutcome, { cls: string; label: string; text: string }> = {
  MATCH: { cls: "s-ok", label: "✓ Coincide", text: "Los datos del DUI que confirmó la persona coinciden con el RNPN." },
  NO_MATCH: {
    cls: "s-abandoned",
    label: "✕ No coincide",
    text: "El RNPN respondió, pero algunos datos del DUI que confirmó la persona no coinciden.",
  },
  UNAVAILABLE: {
    cls: "s-waiting",
    label: "○ No disponible",
    text: "El RNPN no respondió después de los reintentos. La solicitud queda en espera y no se aprueba en automático.",
  },
};

export const RNPN_FIELD: Record<RnpnField, string> = {
  DUI: "Número de DUI",
  FIRST_NAMES: "Nombres",
  LAST_NAMES: "Apellidos",
  BIRTH_DATE: "Fecha de nacimiento",
};

const attemptsText = (n: number) => `${n} ${n === 1 ? "intento" : "intentos"}`;

/**
 * Result of the check of the DUI data against the RNPN (VDI-113). The result never decides by itself: it only adds to
 * the score. While the RNPN is the stand-in service the card says it is simulated.
 */
export function RnpnCheckCard({ check }: { check: RnpnCheck | null | undefined }) {
  if (!check) {
    return (
      <div>
        <p className="m-0 text-[15px] font-semibold">Sin cotejo con el RNPN</p>
        <p className="mt-1 mb-0 text-sm leading-5 text-muted">El cotejo se hace cuando la persona envía la solicitud.</p>
      </div>
    );
  }

  const outcome = OUTCOME[check.outcome] ?? { cls: "b-none", label: check.outcome, text: "" };
  const mismatched = check.outcome === "NO_MATCH" ? (check.mismatchedFields ?? []) : [];
  const rows: Pair[] = [
    { k: "Fecha del cotejo", v: formatDateTime(check.checkedAt) },
    { k: "Intentos", v: attemptsText(check.attempts) },
  ];
  if (check.outcome === "NO_MATCH") {
    rows.push({
      k: "No coinciden",
      v: mismatched.length ? (
        <ul className="m-0 list-none p-0">
          {mismatched.map((f) => (
            <li key={f} className="font-semibold text-danger">
              ✕ {RNPN_FIELD[f] ?? f}
            </li>
          ))}
        </ul>
      ) : (
        <span className="text-muted">El RNPN no indicó qué datos.</span>
      ),
    });
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-2.5">
        <span className={`badge ${outcome.cls} px-3.5 py-[5px] text-[15px]`}>{outcome.label}</span>
        {check.simulated && (
          <span className="badge b-none" title="Todavía no hay conexión con el RNPN real">
            Simulado
          </span>
        )}
      </div>
      {outcome.text && <p className="m-0 text-base leading-6">{outcome.text}</p>}
      <KeyValue items={rows} />
      {check.simulated && (
        <p className="m-0 text-sm leading-5 text-muted">
          Resultado de un servicio simulado: todavía no hay conexión con el RNPN real.
        </p>
      )}
    </div>
  );
}
