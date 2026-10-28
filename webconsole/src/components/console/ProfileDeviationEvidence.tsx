"use client";

import { KeyValue } from "@/components/console/Ui";
import { PROFILE_DEVIATION, usd } from "@/lib/console/format";

const num = (v: unknown) => (typeof v === "number" ? v : null);

/**
 * Which aspect of the declared profile broke, for an alert raised by the profile tracking (VDI-138).
 * Returns nothing for any other alert. The evidence comes from the API, so every value is checked.
 */
export function ProfileDeviationEvidence({ evidence }: { evidence: Record<string, unknown> | null | undefined }) {
  if (evidence?.source !== "PROFILE_TRACKING") return null;
  const kind = typeof evidence.deviation === "string" ? PROFILE_DEVIATION[evidence.deviation] : undefined;
  const show = (v: unknown) => {
    const n = num(v);
    if (n === null) return "—";
    return kind?.unit === "count" ? `${n} movimientos` : usd(n);
  };
  const tolerance = num(evidence.tolerancePercent);

  return (
    <section className="flex flex-col gap-2" aria-label="Aspecto del perfil que se rompió">
      <div className="text-sm font-extrabold">Aspecto del perfil que se rompió</div>
      <div className="text-base leading-6 font-semibold">{kind?.label ?? "Desviación del perfil declarado"}</div>
      <KeyValue
        className="grid-cols-[100px_1fr]"
        items={[
          ...(typeof evidence.month === "string" ? [{ k: "Mes", v: evidence.month }] : []),
          { k: "Declarado", v: show(evidence.declared) },
          { k: "Real", v: show(evidence.actual) },
          { k: "Límite", v: show(evidence.limit) },
          ...(tolerance !== null ? [{ k: "Tolerancia", v: `${tolerance} %` }] : []),
        ]}
      />
    </section>
  );
}
