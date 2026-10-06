"use client";

import Link from "next/link";
import type { CSSProperties } from "react";
import {
  Cargando,
  Encabezado,
  ErrorCarga,
  FiltroSelect,
  FiltroTexto,
  mensajeDeError,
  Paginacion,
  PanelFiltros,
  useCarga,
  useFiltrosPaginados,
  Vacio,
} from "@/components/consola/ui";
import { apiFetch, todasLasSolicitudes, type Catalogos, type SolicitudResumen } from "@/lib/consola/api";
import { ESTADO_SOLICITUD, fecha, fechaCorta, NOTA_HORA, RIESGO } from "@/lib/consola/formato";

const FILTROS_INICIALES = {
  numero: "",
  nombre: "",
  fecha: "todas",
  tipo: "todos",
  monto: "todos",
  riesgo: "todos",
  estado: "todos",
};

const SIN_DECLARAR = "__sin__";
const SIN_NUMERO = "Sin número aún";

function cumple(r: SolicitudResumen, f: typeof FILTROS_INICIALES) {
  const numero = f.numero.trim().toLowerCase();
  const nombre = f.nombre.trim().toLowerCase();
  if (numero && !(r.number ?? SIN_NUMERO).toLowerCase().includes(numero)) return false;
  if (nombre && !(r.name ?? "").toLowerCase().includes(nombre)) return false;
  if (f.fecha !== "todas" && fecha(r.date) !== f.fecha) return false;
  if (f.tipo === SIN_DECLARAR ? r.transactionTypeLabel : f.tipo !== "todos" && r.transactionTypeLabel !== f.tipo)
    return false;
  if (
    f.monto === SIN_DECLARAR
      ? r.monthlyAmountRangeLabel
      : f.monto !== "todos" && r.monthlyAmountRangeLabel !== f.monto
  )
    return false;
  if (f.riesgo !== "todos" && r.riskLevel !== f.riesgo) return false;
  if (f.estado !== "todos" && r.status !== f.estado) return false;
  return true;
}

/** Opciones de un filtro: las del catálogo y, si los datos traen alguna que no esté, también esa. */
function opcionesDe(catalogo: string[] | undefined, valores: (string | null)[]) {
  const todas = [...(catalogo ?? [])];
  for (const v of valores) if (v && !todas.includes(v)) todas.push(v);
  return todas.map((x) => ({ value: x, label: x }));
}

export function SolicitudesView() {
  const solicitudes = useCarga("solicitudes", todasLasSolicitudes);
  // Los catálogos solo dan las opciones de los filtros; si fallan, se usan los valores de los datos.
  const catalogos = useCarga("catalogos", (t) => apiFetch<Catalogos>("/api/catalogs", { token: t }));
  const { filtros: f, setFiltro, limpiar, hayFiltros, tamano, setTamano, setPagina, paginar } =
    useFiltrosPaginados(FILTROS_INICIALES);

  const datos = solicitudes.datos ?? [];
  const filtradas = datos.filter((r) => cumple(r, f));
  const pag = paginar(filtradas);
  const fechas = [...new Set(datos.map((r) => fecha(r.date)))];

  return (
    <div className="flex min-w-0 flex-col gap-5">
      <Encabezado titulo="Solicitudes">
        {solicitudes.datos
          ? `${filtradas.length} de ${datos.length} solicitudes de onboarding. ${NOTA_HORA}`
          : `Solicitudes de onboarding recibidas desde la app móvil. ${NOTA_HORA}`}
      </Encabezado>

      <PanelFiltros etiqueta="solicitudes" hayFiltros={hayFiltros} onLimpiar={limpiar}>
        <FiltroTexto
          id="f-num"
          label="Solicitud"
          placeholder="Por ejemplo, SOL-2026-00418"
          value={f.numero}
          onChange={(v) => setFiltro("numero", v)}
        />
        <FiltroTexto
          id="f-nom"
          label="Nombre"
          placeholder="Por ejemplo, Rivas"
          value={f.nombre}
          onChange={(v) => setFiltro("nombre", v)}
        />
        <FiltroSelect
          id="f-fecha"
          label="Fecha"
          value={f.fecha}
          onChange={(v) => setFiltro("fecha", v)}
          opciones={[{ value: "todas", label: "Todas" }, ...fechas.map((x) => ({ value: x, label: x }))]}
        />
        <FiltroSelect
          id="f-tipo"
          label="Tipo de dinero"
          value={f.tipo}
          onChange={(v) => setFiltro("tipo", v)}
          opciones={[
            { value: "todos", label: "Todos" },
            ...opcionesDe(
              catalogos.datos?.transactionTypes.map((x) => x.label),
              datos.map((r) => r.transactionTypeLabel),
            ),
            { value: SIN_DECLARAR, label: "Sin declarar" },
          ]}
        />
        <FiltroSelect
          id="f-monto"
          label="Monto mensual"
          value={f.monto}
          onChange={(v) => setFiltro("monto", v)}
          opciones={[
            { value: "todos", label: "Todos" },
            ...opcionesDe(
              catalogos.datos?.monthlyAmountRanges.map((x) => x.label),
              datos.map((r) => r.monthlyAmountRangeLabel),
            ),
            { value: SIN_DECLARAR, label: "Sin declarar" },
          ]}
        />
        <FiltroSelect
          id="f-r"
          label="Nivel de riesgo"
          value={f.riesgo}
          onChange={(v) => setFiltro("riesgo", v)}
          opciones={[
            { value: "todos", label: "Todos" },
            { value: "LOW", label: "Bajo" },
            { value: "MEDIUM", label: "Medio" },
            { value: "HIGH", label: "Alto" },
            { value: "PENDING_REVIEW", label: "Pendiente de evaluación" },
            { value: "NOT_EVALUATED", label: "Sin evaluar" },
          ]}
        />
        <FiltroSelect
          id="f-e"
          label="Estado"
          value={f.estado}
          onChange={(v) => setFiltro("estado", v)}
          opciones={[
            { value: "todos", label: "Todos" },
            { value: "IN_PROGRESS", label: "En progreso" },
            { value: "COMPLETED", label: "Completada" },
            { value: "ABANDONED", label: "Abandonada" },
          ]}
        />
      </PanelFiltros>

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

        {solicitudes.cargando && (
          <Cargando
            etiqueta="solicitudes"
            className="md:min-w-[1100px]"
            grid="g-sol"
            anchos={["110px", "70%", "90px", "110px", "60px", "120px", "90px"]}
          />
        )}
        {solicitudes.error && (
          <ErrorCarga
            titulo="No pudimos cargar la información"
            texto={mensajeDeError(
              solicitudes.error,
              "Hubo un problema con el servidor. Tus datos no se han perdido; intenta de nuevo.",
            )}
            onRetry={solicitudes.recargar}
          />
        )}
        {solicitudes.datos && datos.length === 0 && (
          <Vacio
            titulo="Todavía no hay solicitudes"
            texto="Cuando alguien envíe su solicitud desde la app, aparecerá aquí."
          />
        )}
        {datos.length > 0 && filtradas.length === 0 && (
          <Vacio
            titulo="No encontramos solicitudes"
            texto="Prueba con otros filtros, o quítalos para ver todas."
            onLimpiar={hayFiltros ? limpiar : undefined}
          />
        )}
        {pag.visibles.map((r, i) => {
          const rk = RIESGO[r.riskLevel];
          const est = ESTADO_SOLICITUD[r.status];
          const nombre = r.name || "Sin nombre aún";
          return (
            <div key={r.id} className="gr g-sol anim-fila" style={{ "--i": i } as CSSProperties} role="row">
              <div role="cell">
                <Link href={`/solicitudes/${r.id}`} className="rowlink u" aria-label={`Abrir solicitud de ${nombre}`}>
                  {r.number || SIN_NUMERO}
                </Link>
              </div>
              <div role="cell" className={r.name ? "font-semibold" : "text-muted"}>
                {nombre}
              </div>
              <div role="cell" data-label="Fecha" className="text-ink-soft">
                {fechaCorta(r.date)}
              </div>
              <div role="cell" data-label="Tipo de dinero">
                {r.transactionTypeLabel || "—"}
              </div>
              <div role="cell" data-label="Monto mensual" className="font-semibold">
                {r.monthlyAmountRangeLabel || "—"}
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

      {filtradas.length > 0 && (
        <Paginacion
          etiqueta="solicitudes"
          total={pag.total}
          pagina={pag.pagina}
          totalPaginas={pag.totalPaginas}
          tamano={tamano}
          onPagina={setPagina}
          onTamano={setTamano}
        />
      )}
    </div>
  );
}
