"use client";

import type { RiskDecision } from "@/lib/console/api";
import { formatDateTime } from "@/lib/console/format";

const DECISION: Record<RiskDecision["decision"], { cls: string; label: string }> = {
  ENHANCED_REVIEW: { cls: "s-enhanced", label: "⚑ Revisión reforzada" },
  AUTOMATIC_FLOW: { cls: "s-ok", label: "✓ Flujo automático" },
};

/** Why the rule decided what it did, in Spanish: the API `reason` comes in English. */
function reason(d: RiskDecision) {
  const held = d.decision === "ENHANCED_REVIEW";
  return `El score ${d.score} ${held ? "supera" : "no supera"} el umbral de revisión reforzada de ${d.threshold}: ${
    held ? "la solicitud no se aprueba en automático y espera revisión" : "la solicitud sigue el flujo automático"
  }.`;
}

const who = (decidedBy: string | null) => (!decidedBy || decidedBy === "SYSTEM" ? "Sistema (regla automática)" : decidedBy);

/**
 * Audit log of the risk decisions of a request (VDI-119), oldest first. Read-only: the API never lets anyone edit or
 * delete a decision, so there are no actions here.
 */
export function RiskDecisionLog({ decisions }: { decisions: RiskDecision[] | null | undefined }) {
  if (!decisions?.length) return <p className="m-0 text-[15px] text-muted">Sin decisiones registradas.</p>;

  return (
    <ol className="m-0 flex list-none flex-col gap-4 p-0" aria-label="Bitácora de decisiones">
      {decisions.map((d) => {
        const decision = DECISION[d.decision] ?? { cls: "b-none", label: d.decision };
        return (
          <li key={d.id} className="border-l-4 border-line-mid pl-4">
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5">
              <span className={`badge ${decision.cls}`}>{decision.label}</span>
              <time dateTime={d.decidedAt} className="text-sm text-ink-soft tabular-nums">
                {formatDateTime(d.decidedAt)}
              </time>
            </div>
            <p className="mt-2 mb-1 text-[15px] leading-[22px]">{reason(d)}</p>
            <p className="m-0 text-sm leading-5 text-muted">
              Score {d.score} · umbral {d.threshold} · modelo de score v{d.modelVersion} · decidió: {who(d.decidedBy)}
            </p>
          </li>
        );
      })}
    </ol>
  );
}
