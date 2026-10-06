"use client";

import Link from "next/link";
import { useState, type CSSProperties } from "react";
import { Cargando, Encabezado, ErrorCarga, useEstadoDatos, Vacio } from "@/components/consola/ui";
import {
  completada,
  estadoSolicitud,
  fechaCorta,
  numeroDe,
  riesgoDe,
  SOLICITUDES,
  usd,
} from "@/lib/consola/datos";

export function SolicitudesView() {
  const { estado, reintentar } = useEstadoDatos();
  const [q, setQ] = useState("");
  const [fRiesgo, setFRiesgo] = useState("todos");
  const [fEstado, setFEstado] = useState("todos");

  const ql = q.trim().toLowerCase();
  const filtradas = SOLICITUDES.filter((r) => {
    if (ql && !r.nombre.toLowerCase().includes(ql) && !r.num.toLowerCase().includes(ql)) return false;
    if (fRiesgo !== "todos" && riesgoDe(r).key !== fRiesgo) return false;
    if (fEstado === "progreso" && completada(r)) return false;
    if (fEstado === "completada" && !completada(r)) return false;
    return true;
  });
  const hayFiltros = !!(ql || fRiesgo !== "todos" || fEstado !== "todos");
  const limpiar = () => {
    setQ("");
    setFRiesgo("todos");
    setFEstado("todos");
  };

  return (
    <div className="flex min-w-0 flex-col gap-5">
      <Encabezado titulo="Solicitudes">
        {estado === "normal"
          ? `${filtradas.length} de ${SOLICITUDES.length} solicitudes de onboarding.`
          : "Solicitudes de onboarding recibidas desde la app móvil."}
      </Encabezado>

      <div className="flex flex-wrap items-end gap-x-6 gap-y-4">
        <div className="flex-[1_1_260px] sm:max-w-[380px]">
          <label htmlFor="q" className="lbl">
            Buscar por nombre o número
          </label>
          <input
            id="q"
            className="fld"
            type="search"
            placeholder="Por ejemplo, Rivas o SOL-2026-00418"
            value={q}
            onChange={(e) => setQ(e.target.value)}
          />
        </div>
        <div className="min-w-[180px] flex-[0_1_220px] max-sm:basis-full">
          <label htmlFor="f-r" className="lbl">
            Nivel de riesgo
          </label>
          <select id="f-r" className="fld" value={fRiesgo} onChange={(e) => setFRiesgo(e.target.value)}>
            <option value="todos">Todos</option>
            <option value="bajo">Bajo</option>
            <option value="pend">Pendiente de evaluación</option>
            <option value="sin">Sin evaluar</option>
          </select>
        </div>
        <div className="min-w-[160px] flex-[0_1_200px] max-sm:basis-full">
          <label htmlFor="f-e" className="lbl">
            Estado
          </label>
          <select id="f-e" className="fld" value={fEstado} onChange={(e) => setFEstado(e.target.value)}>
            <option value="todos">Todos</option>
            <option value="progreso">En progreso</option>
            <option value="completada">Completada</option>
          </select>
        </div>
      </div>

      <div className="tblwrap" role="table" aria-label="Solicitudes de onboarding">
        <div className="gh g-sol" role="row">
          <div role="columnheader">Solicitud</div>
          <div role="columnheader">Nombre</div>
          <div role="columnheader">Fecha</div>
          <div role="columnheader">Tipo de dinero</div>
          <div role="columnheader">Monto mensual</div>
          <div role="columnheader">Nivel de riesgo</div>
          <div role="columnheader">Estado</div>
        </div>

        {estado === "cargando" && (
          <Cargando
            etiqueta="solicitudes"
            className="md:min-w-[1060px]"
            grid="g-sol"
            anchos={["110px", "70%", "90px", "110px", "60px", "120px", "90px"]}
          />
        )}
        {estado === "error" && (
          <ErrorCarga
            titulo="No pudimos cargar la información"
            texto="Hubo un problema de conexión con el servidor. Tus datos no se han perdido."
            onRetry={reintentar}
          />
        )}
        {estado === "vacio" && (
          <Vacio
            titulo="Todavía no hay solicitudes"
            texto="Cuando alguien envíe su solicitud desde la app, aparecerá aquí."
          />
        )}
        {estado === "normal" && filtradas.length === 0 && (
          <Vacio
            titulo="No encontramos solicitudes"
            texto="Prueba con otro nombre o número, o quita los filtros."
            onLimpiar={hayFiltros ? limpiar : undefined}
          />
        )}
        {estado === "normal" &&
          filtradas.map((r, i) => {
            const rk = riesgoDe(r);
            const est = estadoSolicitud(r);
            return (
              <div key={r.id} className="gr g-sol anim-fila" style={{ "--i": i } as CSSProperties} role="row">
                <div role="cell">
                  <Link
                    href={`/solicitudes/${r.id}`}
                    className="rowlink u"
                    aria-label={`Abrir solicitud de ${r.nombre}`}
                  >
                    {numeroDe(r)}
                  </Link>
                </div>
                <div role="cell" className="font-semibold">
                  {r.nombre}
                </div>
                <div role="cell" data-label="Fecha" className="text-ink-soft">
                  {fechaCorta(r)}
                </div>
                <div role="cell" data-label="Tipo de dinero">{r.tipo || "—"}</div>
                <div role="cell" data-label="Monto mensual" className="font-semibold">
                  {r.monto == null ? "—" : usd(r.monto)}
                </div>
                <div role="cell" data-label="Nivel de riesgo">
                  <span className={`badge ${rk.cls}`}>{rk.label}</span>
                </div>
                <div role="cell" data-label="Estado">
                  <span className={`badge ${est.cls}`}>{est.label}</span>
                </div>
              </div>
            );
          })}
      </div>
    </div>
  );
}
