"use client";

import Link from "next/link";
import { Encabezado, ErrorCarga, KeyValue, mensajeDeError, useCarga, type Par } from "@/components/consola/ui";
import { apiFetch, type Pagina, type ReglaScore, type SolicitudResumen } from "@/lib/consola/api";
import { ESTADO_REGLA, fecha, RIESGO, usd } from "@/lib/consola/formato";

function Regla({ regla: r }: { regla: ReglaScore }) {
  const resultado = RIESGO[r.resultIfMatched];
  const pares: Par[] = [
    { k: "Identificador", v: `${r.code} · ${r.name}` },
    { k: "Umbral", v: r.threshold != null ? `${usd(r.threshold, r.currency ?? "USD")} al mes` : "—" },
    { k: "Resultado", v: <span className={`badge ${resultado.cls}`}>{resultado.label}</span> },
  ];
  if (r.resultIfNotMatched) {
    const otro = RIESGO[r.resultIfNotMatched];
    pares.push({ k: "Si no se cumple", v: <span className={`badge ${otro.cls}`}>{otro.label}</span> });
  }
  if (r.validFrom) pares.push({ k: "Vigente desde", v: fecha(r.validFrom) });
  if (r.version != null) pares.push({ k: "Versión", v: String(r.version) });
  pares.push({
    k: "Estado de la regla",
    v: r.statusNote ? `${ESTADO_REGLA[r.status]}. ${r.statusNote}` : ESTADO_REGLA[r.status],
  });

  return (
    <section className="card" aria-labelledby={`h-${r.code}`}>
      <h2 id={`h-${r.code}`} className="mt-0 mb-4 font-display text-lg font-extrabold">
        {r.status === "RETIRED" ? "Regla retirada" : "Regla vigente"}
      </h2>
      {r.description && (
        <p className="mt-0 mb-5 font-display text-2xl leading-[1.25] font-extrabold">{r.description}</p>
      )}
      <KeyValue items={pares} />
    </section>
  );
}

export function ReglaView() {
  const reglas = useCarga("reglas", (t) => apiFetch<ReglaScore[]>("/api/console/score-rules", { token: t }));
  // Una solicitud real con riesgo bajo para ilustrar la regla.
  const ejemplo = useCarga("ejemplo-riesgo-bajo", (t) =>
    apiFetch<Pagina<SolicitudResumen>>("/api/console/requests?riskLevel=LOW&size=1", { token: t }),
  );
  const muestra = ejemplo.datos?.content[0];
  const umbral = reglas.datos?.find((r) => r.resultIfMatched === "LOW" && r.status !== "RETIRED");

  return (
    <div className="anim-escalonado flex max-w-[860px] min-w-0 flex-col gap-5">
      <Encabezado titulo="Regla de score">Así se asigna hoy el nivel de riesgo a cada solicitud.</Encabezado>

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

      {reglas.cargando && (
        <div className="card flex flex-col gap-3" role="status" aria-label="Cargando regla">
          <div className="skel w-40" />
          <div className="skel h-7 w-4/5" />
          <div className="skel w-2/3" />
          <div className="skel w-1/2" />
        </div>
      )}
      {reglas.error && (
        <div className="card p-0!">
          <ErrorCarga
            titulo="No pudimos cargar la regla"
            texto={mensajeDeError(reglas.error, "Hubo un problema con el servidor. Intenta de nuevo en un momento.")}
            onRetry={reglas.recargar}
          />
        </div>
      )}
      {reglas.datos?.length === 0 && (
        <div className="card">
          <p className="m-0 text-[15px] text-muted">No hay reglas de score configuradas.</p>
        </div>
      )}
      {reglas.datos?.map((r) => <Regla key={`${r.code}-${r.version}`} regla={r} />)}

      {muestra && (
        <section className="card" aria-labelledby="h-ej">
          <h2 id="h-ej" className="mt-0 mb-2 font-display text-lg font-extrabold">
            Un ejemplo real
          </h2>
          <p className="mt-0 mb-3.5 text-base leading-6">
            {muestra.name || "Una solicitud"} declaró un monto mensual de{" "}
            <strong>{muestra.monthlyAmountRangeLabel || "—"}</strong>
            {umbral?.threshold != null && (
              <>, que no supera {usd(umbral.threshold, umbral.currency ?? "USD")}</>
            )}
            , así que su solicitud quedó con riesgo bajo.
          </p>
          <Link href={`/solicitudes/${muestra.id}`} className="btn2">
            Ver esa solicitud
          </Link>
        </section>
      )}
    </div>
  );
}
