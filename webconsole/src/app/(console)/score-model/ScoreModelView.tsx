"use client";

import { PageHeading, LoadError, KeyValue, type Pair } from "@/components/console/Ui";
import { errorMessage, useLoad } from "@/lib/console/hooks";
import { apiFetch, type ScoreModel } from "@/lib/console/api";
import { RISK, formatDate } from "@/lib/console/format";

const RANGES = [
  { key: "low", level: "LOW" },
  { key: "medium", level: "MEDIUM" },
  { key: "high", level: "HIGH" },
] as const;

function ModelCards({ model }: { model: ScoreModel }) {
  const info: Pair[] = [
    { k: "Modelo", v: `${model.name} (versión ${model.version})` },
    ...(model.validFrom ? [{ k: "Vigente desde", v: formatDate(model.validFrom) }] : []),
  ];

  return (
    <>
      <section className="card" aria-labelledby="h-modelo">
        <h2 id="h-modelo" className="mt-0 mb-4 font-display text-lg font-extrabold">
          Modelo vigente
        </h2>
        <p className="mt-0 mb-5 font-display text-2xl leading-[1.25] font-extrabold">
          El score va de 0 a 100 y suma los puntos de cada señal disponible.
        </p>
        <KeyValue items={info} />
      </section>

      <section className="card" aria-labelledby="h-rangos">
        <h2 id="h-rangos" className="mt-0 mb-4 font-display text-lg font-extrabold">
          Rangos del score
        </h2>
        <KeyValue
          items={RANGES.map(({ key, level }) => ({
            k: `${model[key].from} a ${model[key].to}`,
            v: <span className={`badge ${RISK[level].cls}`}>{RISK[level].label}</span>,
          }))}
        />
      </section>

      <section className="card" aria-labelledby="h-senales">
        <h2 id="h-senales" className="mt-0 mb-1 font-display text-lg font-extrabold">
          Puntos por señal
        </h2>
        <p className="mt-0 mb-4 text-sm leading-5 text-muted">
          Lo máximo que cada señal puede sumar. Una señal que no se capturó no suma ni resta, y las que aún no tienen
          peso no cuentan.
        </p>
        <KeyValue
          items={model.weights.map((w) => ({
            k: w.name,
            v: w.maxPoints == null ? <span className="text-muted">Sin peso aún</span> : `Hasta ${w.maxPoints} puntos`,
          }))}
        />
      </section>
    </>
  );
}

export function ScoreModelView() {
  const model = useLoad("modelo-score", (t) => apiFetch<ScoreModel>("/api/console/score-model", { token: t }));

  return (
    <div className="anim-stagger flex max-w-[860px] min-w-0 flex-col gap-5">
      <PageHeading title="Modelo de score">Así se calcula hoy el score de riesgo de cada solicitud.</PageHeading>

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
          <strong>Solo lectura.</strong> El modelo no se puede editar en esta versión.
        </span>
      </div>

      {model.loading && (
        <div className="card flex flex-col gap-3" role="status" aria-label="Cargando modelo">
          <div className="skel w-40" />
          <div className="skel h-7 w-4/5" />
          <div className="skel w-2/3" />
          <div className="skel w-1/2" />
        </div>
      )}
      {model.error && (
        <div className="card p-0!">
          <LoadError
            title="No pudimos cargar el modelo"
            text={errorMessage(model.error, "Hubo un problema con el servidor. Intenta de nuevo en un momento.")}
            onRetry={model.reload}
          />
        </div>
      )}
      {model.data && <ModelCards model={model.data} />}
    </div>
  );
}
