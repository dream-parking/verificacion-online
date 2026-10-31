"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { LoadError, KeyValue, type Pair } from "@/components/console/Ui";
import { RiskDecisionLog } from "@/components/console/RiskDecisionLog";
import { BindingDossierCard } from "@/components/console/BindingDossierCard";
import { CaseOutcomeCard } from "@/components/console/CaseOutcomeCard";
import { RnpnCheckCard } from "@/components/console/RnpnCheckCard";
import { CustomerNumber } from "@/components/console/CustomerNumber";
import { LocationMap } from "./LocationMap";
import { DocumentPhotos } from "./DocumentPhotos";
import { ProfileVersions } from "./ProfileVersions";
import { DuiDataTable } from "./DuiDataTable";
import { errorMessage, useLoad } from "@/lib/console/hooks";
import { apiFetch, type RequestDetail } from "@/lib/console/api";
import {
  duration,
  REQUEST_STATUS,
  formatDate,
  formatDateTime,
  formatTime,
  isSubmitted,
  deviceLocationText,
  eventText,
  locationFailureText,
  minSec,
  TIME_NOTE,
  STEPS,
  RISK,
  TYPING_PACE,
} from "@/lib/console/format";

const NOT_DECLARED = "Aún no declarado: la persona no ha llegado a este paso.";

function Card({
  id,
  title,
  help,
  className = "",
  children,
}: {
  id: string;
  title: string;
  help?: string;
  className?: string;
  children: ReactNode;
}) {
  return (
    <section className={`card min-w-0 ${className}`} aria-labelledby={id}>
      <h2 id={id} className={`mt-0 font-display text-lg font-extrabold ${help ? "mb-1" : "mb-4"}`}>
        {title}
      </h2>
      {help && <p className="mt-0 mb-4 text-sm leading-5 text-muted">{help}</p>}
      {children}
    </section>
  );
}

function BackLink() {
  return (
    <div>
      <Link href="/requests" className="btn2">
        ‹ Volver a solicitudes
      </Link>
    </div>
  );
}

const noData = (v: string | null | undefined) => v || "—";

/** Shows "Registrado" and, if the person corrected it later, "Última corrección". */
function entry(registeredAt: string, updatedAt: string | null): Pair[] {
  const pairs: Pair[] = [{ k: "Registrado", v: formatDateTime(registeredAt) }];
  if (updatedAt && updatedAt !== registeredAt) pairs.push({ k: "Última corrección", v: formatDateTime(updatedAt) });
  return pairs;
}

export function RequestDetailView({ id }: { id: string }) {
  const load = useLoad(`request:${id}`, (t) =>
    apiFetch<RequestDetail>(`/api/console/requests/${encodeURIComponent(id)}`, { token: t }),
  );

  if (load.loading) {
    return (
      <div className="flex min-w-0 flex-col gap-5" role="status" aria-label="Cargando solicitud">
        <BackLink />
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

  if (load.error || !load.data) {
    const missing = load.error?.status === 404 || load.error?.status === 400;
    return (
      <div className="flex min-w-0 flex-col gap-5">
        <BackLink />
        <div className="card p-0!">
          {missing ? (
            <div className="flex flex-col items-start gap-2 px-6 py-11">
              <div className="font-display text-xl font-extrabold">No encontramos esta solicitud</div>
              <div className="text-[15px] leading-[22px] text-muted">Puede que el enlace esté mal o que ya no exista.</div>
            </div>
          ) : (
            <LoadError
              title="No pudimos cargar la solicitud"
              text={errorMessage(load.error, "Hubo un problema con el servidor. Intenta de nuevo en un momento.")}
              onRetry={load.reload}
            />
          )}
        </div>
      </div>
    );
  }

  const r = load.data;
  const name = [r.applicant?.firstNames, r.applicant?.lastNames].filter(Boolean).join(" ") || "Sin nombre aún";
  const risk = RISK[r.riskLevel];
  const status = REQUEST_STATUS[r.status];
  const sent = isSubmitted(r.status) && r.submittedAt;
  const lastDecision = r.riskDecisions?.at(-1);
  const s = r.signals;

  const score = r.risk?.score;
  const contributions = r.risk?.signals ?? [];
  // A signal without a weight (internal lists) says nothing about this request, so it is not listed. Neither is the city
  // in scores saved before it left the model: it never added points and its old note ("llega con la ubicación") is no
  // longer true. A city that does carry points is still listed.
  const scoredContributions = contributions.filter(
    (c) => c.maxPoints != null && !(c.signal === "CITY" && c.points == null),
  );
  const rule = r.risk?.ruleCode
    ? `Regla aplicada: ${r.risk.ruleCode}${r.risk.ruleName ? ` · ${r.risk.ruleName}` : ""}`
    : "Ninguna regla aplicada";
  let explanationText = r.risk?.explanation;
  if (!explanationText)
    explanationText =
      r.riskLevel === "NOT_EVALUATED"
        ? "Todavía no hay un monto declarado que evaluar."
        : r.riskLevel === "PENDING_REVIEW"
          ? "La regla vigente no asignó un nivel automático: queda pendiente de evaluación."
          : "Sin explicación registrada.";

  const basic: Pair[] = [
    { k: "Nombre completo", v: name },
    { k: "Número de cliente", v: <CustomerNumber value={r.customerNumber} /> },
    { k: "DUI", v: noData(r.applicant?.dui) },
    { k: "Teléfono celular", v: noData(r.applicant?.mobilePhone) },
    { k: "Fecha de solicitud", v: formatDate(r.startedAt) },
  ];

  const incomeData: Pair[] | null = r.income && [
    {
      k: "Origen",
      v: `${noData(r.income.sourceLabel ?? r.income.sourceCode)}${r.income.sourceDetail ? ` (${r.income.sourceDetail})` : ""}`,
    },
    { k: "Nivel mensual", v: noData(r.income.rangeLabel ?? r.income.rangeCode) },
    ...entry(r.income.registeredAt, r.income.updatedAt),
  ];

  const activity: Pair[] | null = r.expectedActivity && [
    { k: "Tipo de dinero", v: noData(r.expectedActivity.transactionTypeLabel ?? r.expectedActivity.transactionTypeCode) },
    {
      k: "Monto mensual",
      v: noData(r.expectedActivity.monthlyAmountRangeLabel ?? r.expectedActivity.monthlyAmountRangeCode),
    },
    ...entry(r.expectedActivity.registeredAt, r.expectedActivity.updatedAt),
  ];

  // Location resolved by the server from the IP (VDI-67). Older responses only have `approximateLocation`.
  const geo = s?.ipDetails;
  const hasCoordinates = s?.locationStatus === "AVAILABLE" && s.latitude != null && s.longitude != null;
  const locationUnavailable = s?.locationStatus === "UNAVAILABLE";
  const place =
    [...new Set([geo?.city, geo?.regionName, geo?.country].filter(Boolean))].join(", ") || s?.approximateLocation || null;
  const locationRows: Pair[] = !s
    ? []
    : locationUnavailable
      ? [
          { k: "Ubicación aproximada", v: "Ubicación no disponible" },
          {
            k: "Motivo",
            // Without `ipDetails` the server never looked the IP up (the request is older than VDI-67).
            v: geo
              ? locationFailureText(geo.failure)
              : "Todavía no se calculó la ubicación de esta solicitud: se envió antes de que el servidor la calculara.",
          },
        ]
      : [
          { k: "Ubicación aproximada", v: noData(place) },
          ...(hasCoordinates
            ? [{ k: "Coordenadas aproximadas", v: `${s.latitude!.toFixed(4)}, ${s.longitude!.toFixed(4)}` }]
            : []),
          ...(geo?.timezone ? [{ k: "Zona horaria", v: geo.timezone }] : []),
          ...(geo?.lookedUpAt ? [{ k: "Ubicación consultada", v: formatDateTime(geo.lookedUpAt) }] : []),
          ...(geo?.isp ? [{ k: "Proveedor de internet", v: geo.isp }] : []),
        ];

  const signalList: Pair[] = s
    ? [
        { k: "Señales capturadas", v: s.capturedAt ? formatDateTime(s.capturedAt) : "Sin registro de fecha" },
        { k: "Ciudad de la solicitud", v: deviceLocationText(s.deviceLocationStatus, s.deviceLocation) },
        { k: "Dirección IP", v: noData(s.ip) },
        ...locationRows,
        { k: "Huella de dispositivo", v: noData(s.deviceFingerprint) },
        { k: "Dispositivo", v: noData(s.device) },
        {
          k: "Horario",
          v: sent
            ? `Iniciada ${formatTime(r.startedAt)} · enviada ${formatTime(r.submittedAt!)}${s.nightTime ? " · horario nocturno" : ""}`
            : `Iniciada ${formatTime(r.startedAt)} · sin enviar${s.nightTime ? " · horario nocturno" : ""}`,
        },
        {
          k: "Ritmo de escritura",
          v: s.typingPace
            ? `${TYPING_PACE[s.typingPace]}${s.typingSpeedCpm ? `, unos ${s.typingSpeedCpm} caracteres por minuto` : ""}`
            : "—",
        },
        { k: "Duración total", v: s.totalDurationSeconds != null && sent ? duration(s.totalDurationSeconds) : "En curso" },
        {
          k: "Solicitudes desde este dispositivo",
          v: s.requestsFromSameDevice != null ? String(s.requestsFromSameDevice) : "—",
        },
      ]
    : [];

  const steps = STEPS.map(({ step, name }) => {
    const p = r.steps?.find((x) => x.step === step);
    const attempts = p?.attempts && p.attempts > 1 ? ` · ${p.attempts} intentos` : "";
    const speed =
      p?.typingSpeedCps != null ? ` · ${p.typingSpeedCps.toLocaleString("es", { maximumFractionDigits: 1 })} car./s` : "";
    return { t: name, d: p?.durationSeconds != null ? minSec(p.durationSeconds) + attempts + speed : "Sin completar" };
  });

  const timeline = [...(r.timeline ?? [])].sort((a, b) => a.occurredAt.localeCompare(b.occurredAt));

  return (
    <div className="anim-stagger flex min-w-0 flex-col gap-5">
      <BackLink />

      <div className="flex flex-wrap items-end justify-between gap-x-6 gap-y-3">
        <div>
          <h1 className="mt-0 mb-1 font-display text-[26px] leading-[1.1] font-extrabold md:text-[30px] [overflow-wrap:anywhere]">{name}</h1>
          <div className="text-base leading-6 text-ink-soft">
            Solicitud <strong className="text-ink">{r.number || "Sin número aún"}</strong> ·{" "}
            {formatDate(r.submittedAt ?? r.startedAt)} · {formatTime(r.submittedAt ?? r.startedAt)}
          </div>
        </div>
        <div className="flex flex-wrap gap-2.5">
          <span className={`badge ${status.cls}`}>
            {r.status === "IN_PROGRESS" ? `${status.label} (${r.completedSteps} de ${STEPS.length} etapas)` : status.label}
          </span>
          <span className={`badge ${risk.cls}`}>{risk.label}</span>
        </div>
      </div>

      {r.status === "ENHANCED_REVIEW" && (
        <div role="alert" className="flex items-start gap-3 border border-[#7a4b00] bg-[#fff0c2] px-4 py-3 text-[15px] leading-[22px] text-[#3d2600]">
          <span aria-hidden="true" className="text-lg leading-[22px]">
            ⚑
          </span>
          <span>
            <strong>Solicitud en revisión reforzada.</strong> No se aprueba en automático: una persona debe revisarla antes
            de abrir la cuenta.
            {lastDecision?.decision === "ENHANCED_REVIEW" &&
              ` El score ${lastDecision.score} supera el umbral de ${lastDecision.threshold} (modelo de score v${lastDecision.modelVersion}).`}{" "}
            <a href="#h-decisiones" className="font-semibold underline">
              Ver decisiones
            </a>
          </span>
        </div>
      )}

      <div className="anim-stagger flex flex-wrap items-start gap-5">
        <div className="flex min-w-0 flex-[1_1_420px] flex-col gap-5">
          <Card id="h-basicos" title="Datos básicos">
            <KeyValue items={basic} />
          </Card>

          <Card
            id="h-ing"
            title="Ingresos declarados"
            help="Registro con fecha y hora, para el expediente de Conozca a su Cliente."
          >
            {incomeData ? <KeyValue items={incomeData} /> : <p className="m-0 text-[15px] text-muted">{NOT_DECLARED}</p>}
          </Card>

          <Card
            id="h-mov"
            title="Movimiento esperado en la cuenta"
            help="Qué dinero dice la persona que manejará en la cuenta."
          >
            {activity ? <KeyValue items={activity} /> : <p className="m-0 text-[15px] text-muted">{NOT_DECLARED}</p>}
          </Card>
        </div>

        <div className="flex min-w-0 flex-[1_1_420px] flex-col gap-5">
          <Card id="h-score" title="Score de riesgo">
            <div className="mb-3 flex flex-wrap items-center gap-3">
              {score != null && (
                <span className="font-display text-[28px] leading-none font-extrabold">
                  {score}
                  <span className="text-base font-semibold text-muted"> / 100</span>
                </span>
              )}
              <span className={`badge ${risk.cls} px-3.5 py-[5px] text-[15px]`}>{risk.label}</span>
              <span className="text-sm text-muted">
                {score != null && r.risk?.modelVersion != null ? `Modelo de score v${r.risk.modelVersion}` : rule}
              </span>
            </div>
            <p className="m-0 text-base leading-6">{explanationText}</p>
            {score != null && contributions.length > 0 && (
              <div className="mt-4">
                <h3 className="mt-0 mb-1 text-[15px] font-bold">De dónde sale el score</h3>
                <p className="mt-0 mb-2 text-sm leading-5 text-muted">
                  Cada fila suma puntos de riesgo: 0 significa que no se detectó nada raro en esa señal.
                </p>
                <ul className="m-0 flex list-none flex-col gap-3 p-0">
                  {scoredContributions.map((c) => (
                    <li key={c.signal}>
                      <div className="flex flex-wrap items-baseline justify-between gap-x-3">
                        <span className="text-[15px] font-semibold">{c.name}</span>
                        <span className="text-sm whitespace-nowrap">
                          {c.points == null ? (
                            <span className="text-muted">
                              No disponible · 0 de {c.maxPoints}
                            </span>
                          ) : (
                            <>
                              <strong>{c.points}</strong> de {c.maxPoints} puntos
                            </>
                          )}
                        </span>
                      </div>
                      {c.maxPoints != null && (
                        <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-line-strong" aria-hidden="true">
                          <div
                            className="h-full rounded-full bg-ink-soft"
                            style={{ width: `${Math.min(100, ((c.points ?? 0) / c.maxPoints) * 100)}%` }}
                          />
                        </div>
                      )}
                      {c.observation && <p className="mt-1 mb-0 text-sm leading-5 text-muted">{c.observation}</p>}
                    </li>
                  ))}
                </ul>
              </div>
            )}
            {r.risk?.evaluatedAt && (
              <p className="mt-3 mb-0 text-sm text-muted">Evaluado el {formatDateTime(r.risk.evaluatedAt)}</p>
            )}
          </Card>

          <Card
            id="h-rnpn"
            title="Cotejo RNPN"
            help="Los datos del DUI que confirmó la persona, comparados con el Registro Nacional de las Personas Naturales."
          >
            <RnpnCheckCard check={r.rnpn} />
          </Card>
        </div>
      </div>

      <Card
        id="h-decisiones"
        title="Decisiones"
        help="Bitácora de lo que decidió la regla de riesgo al enviar la solicitud. Es de solo lectura: ninguna decisión se edita ni se borra."
      >
        <RiskDecisionLog decisions={r.riskDecisions} />
      </Card>

      <Card
        id="h-desenlace"
        title="Desenlace del caso"
        help="Si el caso terminó en fraude confirmado o en falso positivo. Un fraude confirmado pone el DUI, el teléfono y el dispositivo en las listas internas de riesgo. Corregirlo agrega un registro nuevo y revoca lo que puso el anterior; ninguno se borra."
      >
        <CaseOutcomeCard requestId={r.id} />
      </Card>

      <Card
        id="h-expediente"
        title="Firma y expediente"
        help="El contrato que firmó la persona y la copia inmutable de la evidencia que sustentó la aprobación. Es de solo lectura."
      >
        <BindingDossierCard dossier={r.bindingDossier} />
      </Card>

      <Card
        id="h-perfil"
        title="Perfil transaccional declarado"
        help="Qué dinero dice la persona que moverá en la cuenta. Cada cambio queda como una versión nueva; la vigente es la base del monitoreo."
      >
        <ProfileVersions versions={r.expectedActivity?.versions} />
      </Card>

      <Card
        id="h-sen"
        title="Señales del dispositivo y del comportamiento"
        help="Datos de apoyo para el análisis. No son un veredicto sobre la persona."
      >
        <div className="flex flex-wrap gap-x-10 gap-y-5">
          {s ? (
            <KeyValue items={signalList} className="min-w-0 flex-[1_1_380px]" />
          ) : (
            <div className="min-w-0 flex-[1_1_380px]">
              <p className="m-0 text-[15px] font-semibold">Sin señales capturadas</p>
              <p className="mt-1 mb-0 text-sm leading-5 text-muted">
                La app todavía no las ha enviado, o la persona no aceptó el aviso de privacidad.
              </p>
            </div>
          )}
          <div className="min-w-0 flex-[1_1_300px]">
            <div className="k mb-1.5">Tiempo por paso</div>
            <ul className="m-0 flex list-none flex-col p-0">
              {steps.map((p) => (
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
        {hasCoordinates && (
          <div className="mt-6">
            <div className="k mb-2">Mapa</div>
            <LocationMap latitude={s!.latitude!} longitude={s!.longitude!} place={place} />
          </div>
        )}
      </Card>

      <Card
        id="h-dui"
        title="Fotos del DUI"
        help="Se descifran al abrir esta pantalla y cada consulta queda en la auditoría de accesos."
      >
        {r.identityDocument && <DuiDataTable document={r.identityDocument} />}
        <DocumentPhotos requestId={r.id} />
      </Card>

      <Card id="h-tl" title="Línea de tiempo" help={TIME_NOTE}>
        {timeline.length === 0 ? (
          <p className="m-0 text-[15px] text-muted">Sin eventos registrados.</p>
        ) : (
          <ol className="anim-stagger m-0 flex list-none flex-col p-0">
            {timeline.map((e, i) => (
              <li
                key={`${e.occurredAt}-${i}`}
                className="grid min-h-10 grid-cols-[12px_1fr] items-center gap-x-3.5 py-1.5 sm:flex sm:py-0"
              >
                <time
                  dateTime={e.occurredAt}
                  className="col-start-2 text-sm leading-5 text-ink-soft tabular-nums sm:flex-[0_0_200px]"
                >
                  {formatDateTime(e.occurredAt)}
                </time>
                <span className="size-3 flex-none rounded-full bg-blue" aria-hidden="true" />
                <span className="text-[15px] leading-[22px]">
                  {eventText(e.description, e.type)}
                  {e.actor && <span className="text-muted"> · {e.actor}</span>}
                </span>
              </li>
            ))}
          </ol>
        )}
      </Card>
    </div>
  );
}
