"use client";

import { KeyValue } from "@/components/console/Ui";
import { PROFILE_ALERT_TYPE, PROFILE_DEVIATION, usd } from "@/lib/console/format";

const num = (v: unknown) => (typeof v === "number" ? v : null);

/**
 * Which aspect of the declared profile broke, for an alert raised by the profile tracking (VDI-138).
 * An alert of a profile type that came without that evidence (older or seeded alerts) says what it is about and that
 * the detail is missing. Returns nothing for any other alert. The evidence comes from the API, so every value is checked.
 */
export function ProfileDeviationEvidence({
  evidence,
  typeCode,
}: {
  evidence: Record<string, unknown> | null | undefined;
  typeCode?: string | null;
}) {
  if (evidence?.source !== "PROFILE_TRACKING") {
    const aspect = typeCode ? PROFILE_ALERT_TYPE[typeCode] : undefined;
    if (!aspect) return null;
    return (
      <section className="flex flex-col gap-2" aria-label="Aspecto del perfil que se rompió">
        <div className="text-sm font-extrabold">Aspecto del perfil que se rompió</div>
        <div className="text-base leading-6 font-semibold">{aspect}</div>
        <p className="m-0 text-sm leading-5 text-muted">
          Esta alerta no trae el detalle de la desviación (lo declarado, lo real y la tolerancia). Pasa con las alertas
          anteriores al seguimiento del perfil.
        </p>
      </section>
    );
  }
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
