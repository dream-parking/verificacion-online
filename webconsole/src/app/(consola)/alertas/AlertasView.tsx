"use client";

import { useCallback, useEffect, useRef, useState, type CSSProperties } from "react";
import { useConsola } from "@/components/consola/ConsolaProvider";
import { Cargando, Encabezado, ErrorCarga, KeyValue, useEstadoDatos, Vacio } from "@/components/consola/ui";
import { CRITICIDAD, ESTADO_ALERTA, SEVERIDAD, type Alerta } from "@/lib/consola/datos";

type Tab = "todas" | "mias" | "sin";

export function AlertasView() {
  // Al cambiar de rol se reinician los filtros y el aviso.
  const { rol } = useConsola();
  return <Bandeja key={rol} />;
}

function Bandeja() {
  const { usuario, alertas, tomarAlerta } = useConsola();
  const { estado, reintentar } = useEstadoDatos();
  const [tab, setTab] = useState<Tab>("todas");
  const [aQ, setAQ] = useState("");
  const [aEstado, setAEstado] = useState("todos");
  const [aCrit, setACrit] = useState("todas");
  const [aFecha, setAFecha] = useState("7d");
  const [toast, setToast] = useState("");
  const [selId, setSelId] = useState("");

  const sinAsignar = alertas.filter((x) => x.estado === "Sin asignar").length;
  const mias = alertas.filter((x) => x.resp === usuario.nombre).length;
  const digitos = aQ.replace(/\D/g, "");

  const filtradas = alertas
    .filter((x) => {
      if (tab === "mias" && x.resp !== usuario.nombre) return false;
      if (tab === "sin" && x.estado !== "Sin asignar") return false;
      if (aEstado !== "todos" && x.estado !== aEstado) return false;
      if (aCrit !== "todas" && x.crit !== aCrit) return false;
      if (aFecha === "hoy" && x.d !== 5) return false;
      if (aFecha === "48h" && x.d < 4) return false;
      if (digitos && !x.cuenta.includes(digitos)) return false;
      return true;
    })
    .sort((a, b) => SEVERIDAD[a.crit] - SEVERIDAD[b.crit] || (a.ts < b.ts ? 1 : -1));

  const hayFiltros = !!(digitos || aEstado !== "todos" || aCrit !== "todas" || aFecha !== "7d" || tab !== "todas");
  const limpiar = () => {
    setAQ("");
    setAEstado("todos");
    setACrit("todas");
    setAFecha("7d");
    setTab("todas");
  };
  const tomar = (id: string) => setToast(tomarAlerta(id));
  const seleccionada = alertas.find((x) => x.id === selId);
  const cerrarPreview = useCallback(() => setSelId(""), []);

  let vacioTitulo = "No hay alertas con esos filtros";
  let vacioTexto = "Cambia o quita los filtros para ver más alertas.";
  if (estado === "vacio") {
    vacioTitulo = "Tu bandeja está al día";
    vacioTexto = "No hay alertas por atender en este momento. Cuando el sistema genere una nueva, aparecerá aquí.";
  } else if (tab === "mias" && !hayFiltros) {
    vacioTitulo = "No tienes alertas asignadas";
    vacioTexto = "Revisa «Sin asignar» para tomar una.";
  } else if (tab === "mias") {
    vacioTitulo = "No tienes alertas con esos filtros";
  }

  const tabs: { key: Tab; label: string; count: number }[] = [
    { key: "todas", label: "Todas", count: alertas.length },
    { key: "mias", label: "Mis alertas", count: mias },
    { key: "sin", label: "Sin asignar", count: sinAsignar },
  ];

  return (
    <div className="flex min-w-0 flex-col gap-5">
      <Encabezado titulo="Bandeja de alertas">Ordenadas por criticidad, de la más urgente a la menos urgente.</Encabezado>

      <div className="flex flex-wrap gap-2.5" role="group" aria-label="Filtro rápido">
        {tabs.map((t) => (
          <button key={t.key} type="button" className="tab" aria-pressed={tab === t.key} onClick={() => setTab(t.key)}>
            {t.label}
            <span key={t.count} className="anim-rebote font-extrabold">{t.count}</span>
          </button>
        ))}
      </div>

      <div className="flex flex-wrap items-end gap-x-6 gap-y-4">
        <div className="flex-[1_1_220px] sm:max-w-[300px]">
          <label htmlFor="a-q" className="lbl">
            Buscar por cuenta
          </label>
          <input
            id="a-q"
            className="fld"
            type="search"
            placeholder="Por ejemplo, 4821"
            value={aQ}
            onChange={(e) => setAQ(e.target.value)}
          />
        </div>
        <div className="min-w-[150px] flex-[0_1_180px] max-sm:basis-full">
          <label htmlFor="a-e" className="lbl">
            Estado
          </label>
          <select id="a-e" className="fld" value={aEstado} onChange={(e) => setAEstado(e.target.value)}>
            <option value="todos">Todos</option>
            <option value="Sin asignar">Sin asignar</option>
            <option value="Asignada">Asignada</option>
            <option value="En revisión">En revisión</option>
          </select>
        </div>
        <div className="min-w-[150px] flex-[0_1_180px] max-sm:basis-full">
          <label htmlFor="a-c" className="lbl">
            Criticidad
          </label>
          <select id="a-c" className="fld" value={aCrit} onChange={(e) => setACrit(e.target.value)}>
            <option value="todas">Todas</option>
            <option value="Crítica">Crítica</option>
            <option value="Alta">Alta</option>
            <option value="Media">Media</option>
            <option value="Baja">Baja</option>
          </select>
        </div>
        <div className="min-w-[170px] flex-[0_1_200px] max-sm:basis-full">
          <label htmlFor="a-f" className="lbl">
            Fecha
          </label>
          <select id="a-f" className="fld" value={aFecha} onChange={(e) => setAFecha(e.target.value)}>
            <option value="7d">Últimos 7 días</option>
            <option value="48h">Últimas 48 horas</option>
            <option value="hoy">Hoy</option>
          </select>
        </div>
      </div>

      {toast && (
        <div
          key={toast}
          role="status"
          className="anim-aviso border border-[#0b6b4a] bg-[#e2f5ec] px-4 py-3 text-[15px] leading-[22px] font-semibold text-[#0b4f37]"
        >
          ✓ {toast}
        </div>
      )}

      <div className="flex min-w-0 flex-wrap items-start gap-5">
        <div className="tblwrap min-w-0 flex-[1_1_560px]" role="table" aria-label="Alertas">
          <div className="gh g-ale" role="row">
            <div role="columnheader">Criticidad</div>
            <div role="columnheader">Cuenta</div>
            <div role="columnheader">Motivo</div>
            <div role="columnheader">Estado</div>
            <div role="columnheader">Responsable</div>
            <div role="columnheader">Fecha</div>
            <div role="columnheader">Acción</div>
          </div>

          {estado === "cargando" && (
            <Cargando
              etiqueta="alertas"
              className="md:min-w-[960px]"
              grid="g-ale"
              anchos={["90px", "70px", "75%", "90px", "100px", "70px", "70px"]}
            />
          )}
          {estado === "error" && (
            <ErrorCarga
              titulo="No pudimos cargar las alertas"
              texto="Hubo un problema de conexión con el servidor. Intenta de nuevo en un momento."
              onRetry={reintentar}
            />
          )}
          {(estado === "vacio" || (estado === "normal" && filtradas.length === 0)) && (
            <Vacio
              titulo={vacioTitulo}
              texto={vacioTexto}
              onLimpiar={estado === "normal" && hayFiltros ? limpiar : undefined}
            />
          )}
          {estado === "normal" &&
            filtradas.map((a, i) => (
              <FilaAlerta key={a.id} indice={i} alerta={a} onOpen={() => setSelId(a.id)} onTake={() => tomar(a.id)} />
            ))}
        </div>

        {seleccionada && (
          <VistaPrevia alerta={seleccionada} onClose={cerrarPreview} onTake={() => tomar(seleccionada.id)} />
        )}
      </div>
    </div>
  );
}

function insignias(alerta: Alerta) {
  const [critCls, critLabel] = CRITICIDAD[alerta.crit];
  const [estCls, estLabel] = ESTADO_ALERTA[alerta.estado];
  return { crit: <span className={`badge ${critCls}`}>{critLabel}</span>, est: <span className={`badge ${estCls}`}>{estLabel}</span> };
}

function FilaAlerta({
  alerta: a,
  indice,
  onOpen,
  onTake,
}: {
  alerta: Alerta;
  indice: number;
  onOpen: () => void;
  onTake: () => void;
}) {
  const b = insignias(a);
  return (
    <div className="gr g-ale anim-fila" style={{ "--i": indice } as CSSProperties} role="row">
      <div role="cell">
        <button type="button" className="rowlink" onClick={onOpen} aria-label={`Ver vista previa: ${a.motivo}`}>
          {b.crit}
        </button>
      </div>
      <div role="cell" data-label="Cuenta" className="tabular-nums">
        {a.cuenta}
      </div>
      <div role="cell">{a.motivo}</div>
      <div role="cell" data-label="Estado">{b.est}</div>
      <div role="cell" data-label="Responsable">{a.resp || "—"}</div>
      <div role="cell" data-label="Fecha" className="text-ink-soft">
        {a.fecha}
      </div>
      <div role="cell" className="empty:hidden">
        {a.estado === "Sin asignar" && (
          <button type="button" className="btn btn-take" onClick={onTake} aria-label={`Tomar la alerta de la cuenta ${a.cuenta}`}>
            Tomarla
          </button>
        )}
      </div>
    </div>
  );
}

function VistaPrevia({ alerta: a, onClose, onTake }: { alerta: Alerta; onClose: () => void; onTake: () => void }) {
  const b = insignias(a);
  const cerrar = useRef<HTMLButtonElement>(null);

  // Al abrirse lleva el foco al panel; Escape lo cierra.
  useEffect(() => {
    cerrar.current?.focus();
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [a.id, onClose]);

  return (
    <>
      {/* Por debajo de xl la vista previa es un panel superpuesto; en xl va junto a la tabla. */}
      <div className="anim-aparecer fixed inset-0 z-40 bg-black/40 xl:hidden" aria-hidden="true" onClick={onClose} />
      <aside
        className="card anim-panel fixed inset-x-0 bottom-0 z-50 flex max-h-[80vh] flex-col gap-3.5 overflow-y-auto shadow-[0_-8px_24px_rgba(0,0,0,0.18)] sm:inset-y-0 sm:right-0 sm:left-auto sm:max-h-none sm:w-[360px] xl:sticky xl:top-24 xl:z-auto xl:max-h-[calc(100vh-7rem)] xl:w-auto xl:min-w-[260px] xl:flex-[0_1_320px] xl:shadow-none"
        aria-label="Vista previa de la alerta"
      >
      <div className="flex items-center justify-between gap-2">
        <h2 className="m-0 font-display text-lg font-extrabold">Vista previa</h2>
        <button ref={cerrar} type="button" className="btn2 px-4" onClick={onClose} aria-label="Cerrar vista previa">
          Cerrar
        </button>
      </div>
      <div className="flex flex-wrap gap-2">
        {b.crit}
        {b.est}
      </div>
      <div className="text-base leading-6 font-semibold">{a.motivo}</div>
      <KeyValue
        className="grid-cols-[100px_1fr]"
        items={[
          { k: "Cuenta", v: a.cuenta },
          { k: "Responsable", v: a.resp || "—" },
          { k: "Fecha", v: a.fecha },
        ]}
      />
      <div className="border border-dashed border-line-strong bg-soft px-4 py-3.5 text-sm leading-5 text-ink-soft">
        <strong className="text-ink">Detalle: próximamente.</strong> En esta versión solo puedes ver el resumen y tomar
        alertas sin dueño.
      </div>
      {a.estado === "Sin asignar" && (
        <button type="button" className="btn" onClick={onTake}>
          Tomarla
        </button>
      )}
      </aside>
    </>
  );
}
