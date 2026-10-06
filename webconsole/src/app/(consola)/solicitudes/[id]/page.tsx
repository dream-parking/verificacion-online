import type { ReactNode } from "react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { KeyValue } from "@/components/consola/ui";
import { buscarSolicitud, detalleDe, SOLICITUDES } from "@/lib/consola/datos";

export function generateStaticParams() {
  return SOLICITUDES.map((s) => ({ id: s.id }));
}

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

export default async function DetalleSolicitudPage({ params }: PageProps<"/solicitudes/[id]">) {
  const { id } = await params;
  const r = buscarSolicitud(id);
  if (!r) notFound();
  const d = detalleDe(r);

  return (
    <div className="flex min-w-0 flex-col gap-5">
      <div>
        <Link href="/solicitudes" className="btn2">
          ‹ Volver a solicitudes
        </Link>
      </div>

      <div className="flex flex-wrap items-end justify-between gap-x-6 gap-y-3">
        <div>
          <h1 className="mt-0 mb-1 font-display text-[30px] leading-[1.1] font-extrabold">{r.nombre}</h1>
          <div className="text-base leading-6 text-ink-soft">
            Solicitud <strong className="text-ink">{d.numero}</strong> · {d.fecha}
          </div>
        </div>
        <div className="flex flex-wrap gap-2.5">
          <span className={`badge ${d.estado.cls}`}>{d.estado.label}</span>
          <span className={`badge ${d.riesgo.cls}`}>{d.riesgo.label}</span>
        </div>
      </div>

      <div className="flex flex-wrap items-stretch gap-5">
        <Tarjeta id="h-basicos" titulo="Datos básicos" className="flex-[1_1_420px]">
          <KeyValue items={d.basicos} />
        </Tarjeta>

        <Tarjeta id="h-score" titulo="Score de riesgo" className="flex-[1_1_420px]">
          <div className="mb-3 flex items-center gap-3">
            <span className={`badge ${d.riesgo.cls} px-3.5 py-[5px] text-[15px]`}>{d.riesgo.label}</span>
            <span className="text-sm text-muted">{d.regla}</span>
          </div>
          <p className="m-0 text-base leading-6">{d.explicacion}</p>
        </Tarjeta>

        <Tarjeta
          id="h-ing"
          titulo="Ingresos declarados"
          ayuda="Registro con fecha y hora, para el expediente de Conozca a su Cliente."
          className="flex-[1_1_420px]"
        >
          {d.ingresos ? (
            <KeyValue items={d.ingresos} />
          ) : (
            <p className="m-0 text-[15px] text-muted">{NO_DECLARADO}</p>
          )}
        </Tarjeta>

        <Tarjeta
          id="h-mov"
          titulo="Movimiento esperado en la cuenta"
          ayuda="Qué dinero dice la persona que manejará en la cuenta."
          className="flex-[1_1_420px]"
        >
          {d.movimiento ? (
            <KeyValue items={d.movimiento} />
          ) : (
            <p className="m-0 text-[15px] text-muted">{NO_DECLARADO}</p>
          )}
        </Tarjeta>
      </div>

      <Tarjeta
        id="h-sen"
        titulo="Señales del dispositivo y del comportamiento"
        ayuda="Datos de apoyo para el análisis. No son un veredicto sobre la persona."
      >
        <div className="flex flex-wrap gap-x-10 gap-y-5">
          <KeyValue items={d.senales} className="min-w-0 flex-[1_1_380px]" />
          <div className="min-w-0 flex-[1_1_300px]">
            <div className="k mb-1.5">Tiempo por paso</div>
            <ul className="m-0 flex list-none flex-col p-0">
              {d.pasos.map((p) => (
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

      <Tarjeta id="h-tl" titulo="Línea de tiempo">
        <ol className="m-0 flex list-none flex-col p-0">
          {d.timeline.map((e) => (
            <li key={e.t + e.e} className="flex min-h-10 items-center gap-3.5">
              <time className="flex-[0_0_150px] text-sm leading-5 text-ink-soft tabular-nums">{e.t}</time>
              <span className="size-3 flex-none rounded-full bg-blue" aria-hidden="true" />
              <span className="text-[15px] leading-[22px]">{e.e}</span>
            </li>
          ))}
        </ol>
      </Tarjeta>
    </div>
  );
}
