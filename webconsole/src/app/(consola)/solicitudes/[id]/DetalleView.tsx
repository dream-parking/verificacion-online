"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { ErrorCarga, KeyValue, mensajeDeError, useCarga, type Par } from "@/components/consola/ui";
import { apiFetch, type SolicitudDetalle } from "@/lib/consola/api";
import {
  duracion,
  ESTADO_SOLICITUD,
  fecha,
  fechaHora,
  hora,
  minSeg,
  NOTA_HORA,
  PASOS,
  RIESGO,
  RITMO,
} from "@/lib/consola/formato";

const NO_DECLARADO = "Aún no declarado: la persona no ha llegado a este paso.";

function Tarjeta({
  id,
  titulo,
  ayuda,
  className = "",
  children,
}: {
  id: string;
  titulo: string;
  ayuda?: string;
  className?: string;
  children: ReactNode;
}) {
  return (
    <section className={`card min-w-0 ${className}`} aria-labelledby={id}>
      <h2 id={id} className={`mt-0 font-display text-lg font-extrabold ${ayuda ? "mb-1" : "mb-4"}`}>
        {titulo}
      </h2>
      {ayuda && <p className="mt-0 mb-4 text-sm leading-5 text-muted">{ayuda}</p>}
      {children}
    </section>
  );
}

function Volver() {
  return (
    <div>
      <Link href="/solicitudes" className="btn2">
        ‹ Volver a solicitudes
      </Link>
    </div>
  );
}

const sinDato = (v: string | null | undefined) => v || "—";

/** VDI-41: coordenadas si el solicitante dio permiso; si no, «No disponible» con el motivo. */
function ubicacion(s: NonNullable<SolicitudDetalle["signals"]>): string {
  switch (s.locationStatus) {
    case "AVAILABLE": {
      const coordenadas = `${s.latitude?.toFixed(4)}, ${s.longitude?.toFixed(4)}`;
      const precision = s.locationAccuracyMeters != null ? ` (± ${s.locationAccuracyMeters} m)` : "";
      return `${coordenadas}${precision}${s.approximateLocation ? ` · ${s.approximateLocation}` : ""}`;
    }
    case "PERMISSION_DENIED":
      return "No disponible · el solicitante no dio permiso de ubicación";
    case "UNAVAILABLE":
      return "No disponible · el teléfono no pudo obtener la ubicación";
    default:
      return sinDato(s.approximateLocation);
  }
}

/** «Registrado» y, si la persona lo corrigió después, «Última corrección». */
function registro(registeredAt: string, updatedAt: string | null): Par[] {
  const pares: Par[] = [{ k: "Registrado", v: fechaHora(registeredAt) }];
  if (updatedAt && updatedAt !== registeredAt) pares.push({ k: "Última corrección", v: fechaHora(updatedAt) });
  return pares;
}

export function DetalleView({ id }: { id: string }) {
  const carga = useCarga(`solicitud:${id}`, (t) =>
    apiFetch<SolicitudDetalle>(`/api/console/requests/${encodeURIComponent(id)}`, { token: t }),
  );

  if (carga.cargando) {
    return (
      <div className="flex min-w-0 flex-col gap-5" role="status" aria-label="Cargando solicitud">
        <Volver />
        <div className="skel h-8! w-72 max-w-full" />
        <div className="flex flex-wrap gap-5">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="card flex min-w-0 flex-[1_1_420px] flex-col gap-3">
              <div className="skel w-40" />
              <div className="skel w-3/4" />
              <div className="skel w-2/3" />
            </div>
          ))}
        </div>
      </div>
    );
  }

  if (carga.error || !carga.datos) {
    const noExiste = carga.error?.status === 404 || carga.error?.status === 400;
    return (
      <div className="flex min-w-0 flex-col gap-5">
        <Volver />
        <div className="card p-0!">
          {noExiste ? (
            <div className="flex flex-col items-start gap-2 px-6 py-11">
              <div className="font-display text-xl font-extrabold">No encontramos esta solicitud</div>
              <div className="text-[15px] leading-[22px] text-muted">Puede que el enlace esté mal o que ya no exista.</div>
            </div>
          ) : (
            <ErrorCarga
              titulo="No pudimos cargar la solicitud"
              texto={mensajeDeError(carga.error, "Hubo un problema con el servidor. Intenta de nuevo en un momento.")}
              onRetry={carga.recargar}
            />
          )}
        </div>
      </div>
    );
  }

  const r = carga.datos;
  const nombre = [r.applicant?.firstNames, r.applicant?.lastNames].filter(Boolean).join(" ") || "Sin nombre aún";
  const riesgo = RIESGO[r.riskLevel];
  const estado = ESTADO_SOLICITUD[r.status];
  const enviada = r.status === "COMPLETED" && r.submittedAt;
  const s = r.signals;

  const regla = r.risk?.ruleCode
    ? `Regla aplicada: ${r.risk.ruleCode}${r.risk.ruleName ? ` · ${r.risk.ruleName}` : ""}`
    : "Ninguna regla aplicada";
  let explicacion = r.risk?.explanation;
  if (!explicacion)
    explicacion =
      r.riskLevel === "NOT_EVALUATED"
        ? "Todavía no hay un monto declarado que evaluar."
        : r.riskLevel === "PENDING_REVIEW"
          ? "La regla vigente no asignó un nivel automático: queda pendiente de evaluación."
          : "Sin explicación registrada.";

  const basicos: Par[] = [
    { k: "Nombre completo", v: nombre },
    { k: "DUI", v: sinDato(r.applicant?.dui) },
    { k: "Teléfono celular", v: sinDato(r.applicant?.mobilePhone) },
    { k: "Fecha de solicitud", v: fecha(r.startedAt) },
  ];

  const ingresos: Par[] | null = r.income && [
    {
      k: "Origen",
      v: `${sinDato(r.income.sourceLabel ?? r.income.sourceCode)}${r.income.sourceDetail ? ` (${r.income.sourceDetail})` : ""}`,
    },
    { k: "Nivel mensual", v: sinDato(r.income.rangeLabel ?? r.income.rangeCode) },
    ...registro(r.income.registeredAt, r.income.updatedAt),
  ];

  const movimiento: Par[] | null = r.expectedActivity && [
    { k: "Tipo de dinero", v: sinDato(r.expectedActivity.transactionTypeLabel ?? r.expectedActivity.transactionTypeCode) },
    {
      k: "Monto mensual",
      v: sinDato(r.expectedActivity.monthlyAmountRangeLabel ?? r.expectedActivity.monthlyAmountRangeCode),
    },
    ...registro(r.expectedActivity.registeredAt, r.expectedActivity.updatedAt),
  ];

  const senales: Par[] = s
    ? [
        { k: "Dirección IP", v: sinDato(s.ip) },
        { k: "Ubicación aproximada", v: ubicacion(s) },
        { k: "Huella de dispositivo", v: sinDato(s.deviceFingerprint) },
        { k: "Dispositivo", v: sinDato(s.device) },
        {
          k: "Horario",
          v: enviada
            ? `Iniciada ${hora(r.startedAt)} · enviada ${hora(r.submittedAt!)}${s.nightTime ? " · horario nocturno" : ""}`
            : `Iniciada ${hora(r.startedAt)} · sin enviar${s.nightTime ? " · horario nocturno" : ""}`,
        },
        {
          k: "Ritmo de escritura",
          v: s.typingPace
            ? `${RITMO[s.typingPace]}${s.typingSpeedCpm ? `, unos ${s.typingSpeedCpm} caracteres por minuto` : ""}`
            : "—",
        },
        { k: "Duración total", v: s.totalDurationSeconds != null && enviada ? duracion(s.totalDurationSeconds) : "En curso" },
        {
          k: "Solicitudes desde este dispositivo",
          v: s.requestsFromSameDevice != null ? String(s.requestsFromSameDevice) : "—",
        },
      ]
    : [];

  const pasos = PASOS.map(({ paso, nombre }) => {
    const p = r.steps?.find((x) => x.step === paso);
    const intentos = p?.attempts && p.attempts > 1 ? ` · ${p.attempts} intentos` : "";
    return { t: nombre, d: p?.durationSeconds != null ? minSeg(p.durationSeconds) + intentos : "Sin completar" };
  });

  const timeline = [...(r.timeline ?? [])].sort((a, b) => a.occurredAt.localeCompare(b.occurredAt));

  return (
    <div className="anim-escalonado flex min-w-0 flex-col gap-5">
      <Volver />

      <div className="flex flex-wrap items-end justify-between gap-x-6 gap-y-3">
        <div>
          <h1 className="mt-0 mb-1 font-display text-[26px] leading-[1.1] font-extrabold md:text-[30px]">{nombre}</h1>
          <div className="text-base leading-6 text-ink-soft">
            Solicitud <strong className="text-ink">{r.number || "Sin número aún"}</strong> ·{" "}
            {fecha(r.submittedAt ?? r.startedAt)} · {hora(r.submittedAt ?? r.startedAt)}
          </div>
        </div>
        <div className="flex flex-wrap gap-2.5">
          <span className={`badge ${estado.cls}`}>
            {r.status === "IN_PROGRESS" ? `${estado.label} (${r.completedSteps} de 5 etapas)` : estado.label}
          </span>
          <span className={`badge ${riesgo.cls}`}>{riesgo.label}</span>
        </div>
      </div>

      <div className="anim-escalonado flex flex-wrap items-stretch gap-5">
        <Tarjeta id="h-basicos" titulo="Datos básicos" className="flex-[1_1_420px]">
          <KeyValue items={basicos} />
        </Tarjeta>

        <Tarjeta id="h-score" titulo="Score de riesgo" className="flex-[1_1_420px]">
          <div className="mb-3 flex flex-wrap items-center gap-3">
            <span className={`badge ${riesgo.cls} px-3.5 py-[5px] text-[15px]`}>{riesgo.label}</span>
            <span className="text-sm text-muted">{regla}</span>
          </div>
          <p className="m-0 text-base leading-6">{explicacion}</p>
          {r.risk?.evaluatedAt && (
            <p className="mt-3 mb-0 text-sm text-muted">Evaluado el {fechaHora(r.risk.evaluatedAt)}</p>
          )}
        </Tarjeta>

        <Tarjeta
          id="h-ing"
          titulo="Ingresos declarados"
          ayuda="Registro con fecha y hora, para el expediente de Conozca a su Cliente."
          className="flex-[1_1_420px]"
        >
          {ingresos ? <KeyValue items={ingresos} /> : <p className="m-0 text-[15px] text-muted">{NO_DECLARADO}</p>}
        </Tarjeta>

        <Tarjeta
          id="h-mov"
          titulo="Movimiento esperado en la cuenta"
          ayuda="Qué dinero dice la persona que manejará en la cuenta."
          className="flex-[1_1_420px]"
        >
          {movimiento ? <KeyValue items={movimiento} /> : <p className="m-0 text-[15px] text-muted">{NO_DECLARADO}</p>}
        </Tarjeta>
      </div>

      <Tarjeta
        id="h-sen"
        titulo="Señales del dispositivo y del comportamiento"
        ayuda="Datos de apoyo para el análisis. No son un veredicto sobre la persona."
      >
        <div className="flex flex-wrap gap-x-10 gap-y-5">
          {s ? (
            <KeyValue items={senales} className="min-w-0 flex-[1_1_380px]" />
          ) : (
            <p className="m-0 min-w-0 flex-[1_1_380px] text-[15px] text-muted">
              La app todavía no ha enviado señales de esta solicitud.
            </p>
          )}
          <div className="min-w-0 flex-[1_1_300px]">
            <div className="k mb-1.5">Tiempo por paso</div>
            <ul className="m-0 flex list-none flex-col p-0">
              {pasos.map((p) => (
                <li
                  key={p.t}
                  className="flex justify-between gap-3 border-b border-line-soft py-2 text-[15px] leading-[22px]"
                >
                  <span>{p.t}</span>
                  <span className="font-bold">{p.d}</span>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </Tarjeta>

      <Tarjeta id="h-tl" titulo="Línea de tiempo" ayuda={NOTA_HORA}>
        {timeline.length === 0 ? (
          <p className="m-0 text-[15px] text-muted">Sin eventos registrados.</p>
        ) : (
          <ol className="anim-escalonado m-0 flex list-none flex-col p-0">
            {timeline.map((e, i) => (
              <li
                key={`${e.occurredAt}-${i}`}
                className="grid min-h-10 grid-cols-[12px_1fr] items-center gap-x-3.5 py-1.5 sm:flex sm:py-0"
              >
                <time
                  dateTime={e.occurredAt}
                  className="col-start-2 text-sm leading-5 text-ink-soft tabular-nums sm:flex-[0_0_200px]"
                >
                  {fechaHora(e.occurredAt)}
                </time>
                <span className="size-3 flex-none rounded-full bg-blue" aria-hidden="true" />
                <span className="text-[15px] leading-[22px]">
                  {e.description || e.type}
                  {e.actor && <span className="text-muted"> · {e.actor}</span>}
                </span>
              </li>
            ))}
          </ol>
        )}
      </Tarjeta>
    </div>
  );
}
