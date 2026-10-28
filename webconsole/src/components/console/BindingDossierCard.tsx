"use client";

import type { RequestDetail } from "@/lib/console/api";
import { RISK, formatDateTime } from "@/lib/console/format";
import { RNPN_FIELD } from "./RnpnCheckCard";
import { KeyValue, type Pair } from "./Ui";

type Dossier = NonNullable<RequestDetail["bindingDossier"]>;

const RNPN_OUTCOME = { MATCH: "Coincide", NO_MATCH: "No coincide", UNAVAILABLE: "No disponible" } as const;

const when = (iso: string | null | undefined) => (iso ? formatDateTime(iso) : "—");
const percent = (n: number | null | undefined) => (n == null ? "—" : `${Math.round(n * 100)} %`);

/** Rows of what the dossier froze about the evidence: only what it has, each with the moment it was taken. */
function evidenceRows(evidence: NonNullable<Dossier["evidence"]>): Pair[] {
  const rows: Pair[] = [];
  const { identity, consent, rnpn, liveness, risk, decisions } = evidence;

  if (identity) {
    const name = [identity.firstNames, identity.lastNames].filter(Boolean).join(" ");
    rows.push({ k: "DUI confirmado", v: [name, identity.dui].filter(Boolean).join(" · ") || "—" });
    if (identity.customerNumber) rows.push({ k: "Número de cliente", v: identity.customerNumber });
    rows.push({ k: "DUI confirmado el", v: when(identity.confirmedAt) });
  }
  if (consent) {
    rows.push({
      k: "Consentimiento",
      v: `Aviso ${consent.noticeVersion ?? "—"}, aceptado el ${when(consent.acceptedAt)}`,
    });
  }
  if (rnpn) {
    const outcome = rnpn.outcome ? RNPN_OUTCOME[rnpn.outcome] : "—";
    const mismatched = (rnpn.mismatchedFields ?? []).map((f) => RNPN_FIELD[f] ?? f).join(", ");
    rows.push({
      k: "Cotejo RNPN",
      v: `${outcome}${mismatched ? ` (${mismatched})` : ""}${rnpn.simulated ? " · simulado" : ""}, ${when(rnpn.checkedAt)}`,
    });
  }
  if (liveness) {
    rows.push({
      k: "Prueba de vida",
      v: `${liveness.status === "FAILED" ? "Falló" : "Confianza " + percent(liveness.confidence)}, ${when(liveness.checkedAt)}`,
    });
  }
  const assessment = risk?.assessment;
  if (assessment) {
    const level = assessment.level ? RISK[assessment.level] : null;
    rows.push({
      k: "Score de riesgo",
      v: (
        <>
          {assessment.score ?? "—"} {level && <span className={`badge ${level.cls}`}>{level.label}</span>}
          {risk?.lowMax != null && risk.mediumMax != null && (
            <span className="block text-[13px] leading-[18px] text-muted">
              Umbrales con los que se calculó: Bajo hasta {risk.lowMax}, Medio hasta {risk.mediumMax}
              {risk.reviewThreshold != null ? `, revisión reforzada sobre ${risk.reviewThreshold}` : ""}.
            </span>
          )}
        </>
      ),
    });
  }
  if (decisions) rows.push({ k: "Decisiones", v: `${decisions.length} registradas` });
  return rows;
}

/**
 * The signed contract and the binding dossier of an approved request (VDI-131, VDI-134): a frozen copy of the evidence
 * that backed the approval, kept until `retainedUntil`. It is read only; nothing here edits or deletes it.
 */
export function BindingDossierCard({ dossier }: { dossier: RequestDetail["bindingDossier"] }) {
  if (!dossier) {
    return (
      <div>
        <p className="m-0 text-[15px] font-semibold">Sin contrato firmado</p>
        <p className="mt-1 mb-0 text-sm leading-5 text-muted">
          El expediente de vinculación se arma cuando la persona firma el contrato de una apertura aprobada.
        </p>
      </div>
    );
  }

  const signature = dossier.signature;
  const contract: Pair[] = [
    { k: "Versión del contrato", v: signature?.contractVersion ?? "—" },
    {
      k: "Huella (SHA-256)",
      v: signature?.contractFingerprint ? (
        <code className="text-[13px] [overflow-wrap:anywhere]">{signature.contractFingerprint}</code>
      ) : (
        "—"
      ),
    },
    { k: "Firmado el", v: when(signature?.signedAt) },
  ];
  const keeping: Pair[] = [
    { k: "Congelado el", v: when(dossier.frozenAt) },
    { k: "Se retiene hasta", v: when(dossier.retainedUntil) },
  ];
  const evidence = dossier.evidence ? evidenceRows(dossier.evidence) : [];

  return (
    <div className="flex flex-col gap-5">
      <section aria-label="Contrato firmado" className="flex flex-col gap-2">
        <h3 className="m-0 text-sm font-extrabold">Contrato firmado</h3>
        <KeyValue items={contract} />
      </section>
      <section aria-label="Retención del expediente" className="flex flex-col gap-2">
        <h3 className="m-0 text-sm font-extrabold">Expediente de vinculación</h3>
        <KeyValue items={keeping} />
        <p className="m-0 text-sm leading-5 text-muted">
          Es una copia inmutable de la evidencia al firmar: no se edita y no se borra antes de la fecha de retención.
        </p>
      </section>
      {evidence.length > 0 && (
        <section aria-label="Evidencia congelada" className="flex flex-col gap-2">
          <h3 className="m-0 text-sm font-extrabold">Evidencia que sustentó la aprobación</h3>
          <KeyValue items={evidence} />
        </section>
      )}
    </div>
  );
}
