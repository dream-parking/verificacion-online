"use client";

import Link from "next/link";
import type { CSSProperties } from "react";
import {
  Cargando,
  Encabezado,
  ErrorCarga,
  FiltroSelect,
  FiltroTexto,
  Paginacion,
  PanelFiltros,
  useEstadoDatos,
  useFiltrosPaginados,
  Vacio,
} from "@/components/consola/ui";
import {
  completada,
  estadoSolicitud,
  fechaCorta,
  numeroDe,
  riesgoDe,
  SOLICITUDES,
  usd,
  type Solicitud,
} from "@/lib/consola/datos";

const FILTROS_INICIALES = {
  numero: "",
  nombre: "",
  fecha: "todas",
  tipo: "todos",
  monto: "todos",
  riesgo: "todos",
  estado: "todos",
};

const SIN_DECLARAR = "sin";

// Opciones calculadas a partir de los datos disponibles.
const FECHAS = [...new Set(SOLICITUDES.map((r) => r.f))];
const TIPOS = [...new Set(SOLICITUDES.flatMap((r) => (r.tipo ? [r.tipo] : [])))].sort();

const RANGOS_MONTO: Record<string, (m: number) => boolean> = {
  "menos-500": (m) => m < 500,
  "500-1500": (m) => m >= 500 && m < 1500,
  "1500-mas": (m) => m >= 1500,
};

function cumple(r: Solicitud, f: typeof FILTROS_INICIALES) {
  const numero = f.numero.trim().toLowerCase();
  const nombre = f.nombre.trim().toLowerCase();
  if (numero && !numeroDe(r).toLowerCase().includes(numero)) return false;
  if (nombre && !r.nombre.toLowerCase().includes(nombre)) return false;
  if (f.fecha !== "todas" && r.f !== f.fecha) return false;
  if (f.tipo === SIN_DECLARAR && r.tipo) return false;
  if (f.tipo !== "todos" && f.tipo !== SIN_DECLARAR && r.tipo !== f.tipo) return false;
  if (f.monto === SIN_DECLARAR && r.monto != null) return false;
  if (f.monto in RANGOS_MONTO && (r.monto == null || !RANGOS_MONTO[f.monto](r.monto))) return false;
  if (f.riesgo !== "todos" && riesgoDe(r).key !== f.riesgo) return false;
  if (f.estado === "progreso" && completada(r)) return false;
  if (f.estado === "completada" && !completada(r)) return false;
  return true;
}

export function SolicitudesView() {
  const { estado, reintentar } = useEstadoDatos();
  const { filtros: f, setFiltro, limpiar, hayFiltros, tamano, setTamano, setPagina, paginar } =
    useFiltrosPaginados(FILTROS_INICIALES);

  const filtradas = SOLICITUDES.filter((r) => cumple(r, f));
  const pag = paginar(filtradas);

  return (
    <div className="flex min-w-0 flex-col gap-5">
      <Encabezado titulo="Solicitudes">
        {estado === "normal"
          ? `${filtradas.length} de ${SOLICITUDES.length} solicitudes de onboarding.`
          : "Solicitudes de onboarding recibidas desde la app móvil."}
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
          opciones={[{ value: "todas", label: "Todas" }, ...FECHAS.map((x) => ({ value: x, label: x }))]}
        />
        <FiltroSelect
          id="f-tipo"
          label="Tipo de dinero"
          value={f.tipo}
          onChange={(v) => setFiltro("tipo", v)}
          opciones={[
            { value: "todos", label: "Todos" },
            ...TIPOS.map((x) => ({ value: x, label: x })),
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
            { value: "menos-500", label: "Menos de USD 500" },
            { value: "500-1500", label: "USD 500 a 1,499" },
            { value: "1500-mas", label: "USD 1,500 o más" },
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
            { value: "bajo", label: "Bajo" },
            { value: "pend", label: "Pendiente de evaluación" },
            { value: "sin", label: "Sin evaluar" },
          ]}
        />
        <FiltroSelect
          id="f-e"
          label="Estado"
          value={f.estado}
          onChange={(v) => setFiltro("estado", v)}
          opciones={[
            { value: "todos", label: "Todos" },
            { value: "progreso", label: "En progreso" },
            { value: "completada", label: "Completada" },
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
            texto="Prueba con otros filtros, o quítalos para ver todas."
            onLimpiar={hayFiltros ? limpiar : undefined}
          />
        )}
        {estado === "normal" &&
          pag.visibles.map((r, i) => {
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

      {estado === "normal" && filtradas.length > 0 && (
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
