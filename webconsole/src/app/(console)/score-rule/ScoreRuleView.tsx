"use client";

import Link from "next/link";
import { PageHeading, LoadError, KeyValue, type Pair } from "@/components/console/Ui";
import { errorMessage, useLoad } from "@/lib/console/hooks";
import { apiFetch, type Page, type ScoreRule, type RequestSummary } from "@/lib/console/api";
import { RULE_STATUS, formatDate, RISK, usd } from "@/lib/console/format";

function RuleCard({ rule: r }: { rule: ScoreRule }) {
  const result = RISK[r.resultIfMatched];
  const pairs: Pair[] = [
    { k: "Identificador", v: `${r.code} · ${r.name}` },
    { k: "Umbral", v: r.threshold != null ? `${usd(r.threshold, r.currency ?? "USD")} al mes` : "—" },
    { k: "Resultado", v: <span className={`badge ${result.cls}`}>{result.label}</span> },
  ];
  if (r.resultIfNotMatched) {
    const other = RISK[r.resultIfNotMatched];
    pairs.push({ k: "Si no se cumple", v: <span className={`badge ${other.cls}`}>{other.label}</span> });
  }
  if (r.validFrom) pairs.push({ k: "Vigente desde", v: formatDate(r.validFrom) });
  if (r.version != null) pairs.push({ k: "Versión", v: String(r.version) });
  pairs.push({
    k: "Estado de la regla",
    v: r.statusNote ? `${RULE_STATUS[r.status]}. ${r.statusNote}` : RULE_STATUS[r.status],
  });

  return (
    <section className="card" aria-labelledby={`h-${r.code}`}>
      <h2 id={`h-${r.code}`} className="mt-0 mb-4 font-display text-lg font-extrabold">
        {r.status === "RETIRED" ? "Regla retirada" : "Regla vigente"}
      </h2>
      {r.description && (
        <p className="mt-0 mb-5 font-display text-2xl leading-[1.25] font-extrabold">{r.description}</p>
      )}
      <KeyValue items={pairs} />
    </section>
  );
}

export function ScoreRuleView() {
  const rules = useLoad("reglas", (t) => apiFetch<ScoreRule[]>("/api/console/score-rules", { token: t }));
  // A real request with low risk to illustrate the rule.
  const example = useLoad("ejemplo-riesgo-bajo", (t) =>
    apiFetch<Page<RequestSummary>>("/api/console/requests?riskLevel=LOW&size=1", { token: t }),
  );
  const sample = example.data?.content[0];
  const threshold = rules.data?.find((r) => r.resultIfMatched === "LOW" && r.status !== "RETIRED");

  return (
    <div className="anim-stagger flex max-w-[860px] min-w-0 flex-col gap-5">
      <PageHeading title="Regla de score">Así se asigna hoy el nivel de riesgo a cada solicitud.</PageHeading>

      <div className="flex items-center gap-3 border border-line-mid bg-soft px-4 py-3 text-[15px] leading-[22px]">
        <svg
          className="flex-none"
          width="22"
          height="22"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <rect x="5" y="11" width="14" height="10" rx="2" />
          <path d="M8 11V7a4 4 0 0 1 8 0v4" />
        </svg>
        <span>
          <strong>Solo lectura.</strong> Esta regla no se puede editar en esta versión.
        </span>
      </div>

      {rules.loading && (
        <div className="card flex flex-col gap-3" role="status" aria-label="Cargando regla">
          <div className="skel w-40" />
          <div className="skel h-7 w-4/5" />
          <div className="skel w-2/3" />
          <div className="skel w-1/2" />
        </div>
      )}
      {rules.error && (
        <div className="card p-0!">
          <LoadError
            title="No pudimos cargar la regla"
            text={errorMessage(rules.error, "Hubo un problema con el servidor. Intenta de nuevo en un momento.")}
            onRetry={rules.reload}
          />
        </div>
      )}
      {rules.data?.length === 0 && (
        <div className="card">
          <p className="m-0 text-[15px] text-muted">No hay reglas de score configuradas.</p>
        </div>
      )}
      {rules.data?.map((r) => <RuleCard key={`${r.code}-${r.version}`} rule={r} />)}

      {sample && (
        <section className="card" aria-labelledby="h-ej">
          <h2 id="h-ej" className="mt-0 mb-2 font-display text-lg font-extrabold">
            Un ejemplo real
          </h2>
          <p className="mt-0 mb-3.5 text-base leading-6">
            {sample.name || "Una solicitud"} declaró un monto mensual de{" "}
            <strong>{sample.monthlyAmountRangeLabel || "—"}</strong>
            {threshold?.threshold != null && (
              <>, que no supera {usd(threshold.threshold, threshold.currency ?? "USD")}</>
            )}
            , así que su solicitud quedó con riesgo bajo.
          </p>
          <Link href={`/requests/${sample.id}`} className="btn2">
            Ver esa solicitud
          </Link>
        </section>
      )}
    </div>
  );
}
