"use client";

import { useState } from "react";
import { useConsole } from "@/components/console/ConsoleProvider";
import { PageHeading, LoadError, KeyValue, type Pair } from "@/components/console/Ui";
import { ScoreModelHistory } from "@/components/console/ScoreModelHistory";
import { ScoreThresholdsForm } from "@/components/console/ScoreThresholdsForm";
import { errorMessage, useLoad } from "@/lib/console/hooks";
import { apiFetch, type ScoreModel, type ScoreModelVersion } from "@/lib/console/api";
import { RISK, formatDate } from "@/lib/console/format";
import { fetchScoreModelVersions } from "@/lib/console/scoreModel";

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

function ReadOnlyNotice() {
  return (
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
        <strong>Solo lectura.</strong> Solo un administrador puede cambiar los umbrales del score.
      </span>
    </div>
  );
}

/** History of versions, only for administrators (the API answers 403 to anyone else). */
function History({ versions }: { versions: ReturnType<typeof useLoad<ScoreModelVersion[]>> }) {
  return (
    <section className="flex min-w-0 flex-col gap-3" aria-labelledby="h-historial">
      <div>
        <h2 id="h-historial" className="mt-0 mb-1 font-display text-lg font-extrabold">
          Historial de versiones
        </h2>
        <p className="m-0 text-sm leading-5 text-muted">
          Cada cambio de umbrales es una versión nueva. Ninguna versión anterior se edita ni se borra.
        </p>
      </div>
      {versions.loading && (
        <div className="card flex flex-col gap-3" role="status" aria-label="Cargando historial">
          <div className="skel w-2/3" />
          <div className="skel w-1/2" />
        </div>
      )}
      {versions.error && (
        <div className="card p-0!">
          <LoadError
            title="No pudimos cargar el historial"
            text={errorMessage(versions.error, "Hubo un problema con el servidor. Intenta de nuevo en un momento.")}
            onRetry={versions.reload}
          />
        </div>
      )}
      {versions.data && <ScoreModelHistory versions={versions.data} />}
    </section>
  );
}

export function ScoreModelView() {
  const { user, token } = useConsole();
  const isAdmin = user.role === "ADMIN";
  const model = useLoad("modelo-score", (t) => apiFetch<ScoreModel>("/api/console/score-model", { token: t }));
  const versions = useLoad(isAdmin ? "versiones-modelo-score" : null, fetchScoreModelVersions);
  const [notice, setNotice] = useState<string | null>(null);

  const published = (v: ScoreModelVersion) => {
    setNotice(
      `Publicaste la versión ${v.version}: Bajo hasta ${v.low.to} y Medio hasta ${v.medium.to}. Se aplica a las solicitudes que se evalúen desde ahora.`,
    );
    model.reload();
    versions.reload();
  };

  return (
    <div className="anim-stagger flex max-w-[860px] min-w-0 flex-col gap-5">
      <PageHeading title="Modelo de score">Así se calcula hoy el score de riesgo de cada solicitud.</PageHeading>

      {!isAdmin && <ReadOnlyNotice />}

      {notice && (
        <div
          role="status"
          className="anim-notice flex items-start justify-between gap-3 border border-[#0b6b4a] bg-[#e2f5ec] px-4 py-3 text-[15px] leading-[22px] font-semibold text-[#0b4f37]"
        >
          <span className="[overflow-wrap:anywhere]">✓ {notice}</span>
          <button
            type="button"
            className="-my-1 flex-none rounded-full px-2 text-lg leading-none hover:bg-black/5"
            aria-label="Cerrar aviso"
            onClick={() => setNotice(null)}
          >
            ×
          </button>
        </div>
      )}

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
      {isAdmin && model.data && (
        // A new key after publishing starts the form again from the thresholds now in force.
        <ScoreThresholdsForm key={model.data.version} model={model.data} token={token} onPublished={published} />
      )}
      {isAdmin && <History versions={versions} />}
    </div>
  );
}
