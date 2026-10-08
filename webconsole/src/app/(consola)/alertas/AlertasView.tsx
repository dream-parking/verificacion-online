"use client";

import { useCallback, useEffect, useRef, useState, type CSSProperties } from "react";
import { useConsola } from "@/components/consola/ConsolaProvider";
import {
  Cargando,
  Encabezado,
  ErrorCarga,
  FiltroSelect,
  FiltroTexto,
  KeyValue,
  mensajeDeError,
  Paginacion,
  PanelFiltros,
  useFiltrosPaginados,
  Vacio,
} from "@/components/consola/ui";
import { ApiError, type Alerta } from "@/lib/consola/api";
import { CRITICIDAD, ESTADO_ALERTA, fecha, fechaCorta, NOTA_HORA, puedeTomarAlertas } from "@/lib/consola/formato";

const FILTROS_INICIALES = {
  tab: "todas",
  crit: "todas",
  cuenta: "",
  motivo: "",
  estado: "todos",
  resp: "todos",
  fecha: "todas",
  accion: "todas",
};

const SIN_RESPONSABLE = "__sin__";
const HORA = 3600_000;

/** Límite del filtro de fecha, calculado al elegirlo (no en cada render). */
type Corte = { desde?: number; dia?: string };
function corteDe(opcion: string): Corte {
  const ahora = Date.now();
  if (opcion === "hoy") return { dia: fecha(new Date(ahora).toISOString()) };
  if (opcion === "48h") return { desde: ahora - 48 * HORA };
  if (opcion === "7d") return { desde: ahora - 7 * 24 * HORA };
  return {};
}

type Aviso = { tipo: "ok" | "error"; texto: string };

function mensajeAlTomar(e: unknown) {
  if (e instanceof ApiError) {
    if (e.status === 409) return "Otra persona tomó esta alerta antes que tú, o ya no está abierta. Actualizamos la bandeja.";
    if (e.status === 403) return "Tu rol no puede tomar alertas.";
    if (e.status === 404) return "Esta alerta ya no existe.";
    if (e.status === 0) return "No pudimos conectar con el servidor. Intenta de nuevo.";
  }
  return "No pudimos tomar la alerta. Intenta de nuevo.";
}

export function AlertasView() {
  const { usuario, alertas, tomarAlerta } = useConsola();
  const { filtros: f, setFiltro, limpiar, hayFiltros, tamano, setTamano, setPagina, paginar } =
    useFiltrosPaginados(FILTROS_INICIALES);
  const [corte, setCorte] = useState<Corte>({});
  const [aviso, setAviso] = useState<Aviso | null>(null);
  const [tomando, setTomando] = useState<string | null>(null);
  const [selId, setSelId] = useState("");

  const puedeTomar = puedeTomarAlertas(usuario.role);
  const lista = alertas.datos ?? [];
  const sinAsignar = lista.filter((x) => x.status === "UNASSIGNED").length;
  const mias = lista.filter((x) => x.assigneeId === usuario.id).length;
  const responsables = [...new Set(lista.flatMap((x) => (x.assigneeName ? [x.assigneeName] : [])))].sort();
  const digitos = f.cuenta.replace(/\D/g, "");
  const motivo = f.motivo.trim().toLowerCase();

  // El API ya las devuelve de la más crítica a la menos crítica, y la más reciente primero.
  const filtradas = lista.filter((x) => {
    if (f.tab === "mias" && x.assigneeId !== usuario.id) return false;
    if (f.tab === "sin" && x.status !== "UNASSIGNED") return false;
    if (f.crit !== "todas" && x.criticality !== f.crit) return false;
    if (digitos && !x.account.replace(/\D/g, "").includes(digitos)) return false;
    if (motivo && !x.reason.toLowerCase().includes(motivo)) return false;
    if (f.estado !== "todos" && x.status !== f.estado) return false;
    if (f.resp === SIN_RESPONSABLE && x.assigneeName) return false;
    if (f.resp !== "todos" && f.resp !== SIN_RESPONSABLE && x.assigneeName !== f.resp) return false;
    if (f.fecha !== "todas" && corte.dia && fecha(x.raisedAt) !== corte.dia) return false;
    if (f.fecha !== "todas" && corte.desde && Date.parse(x.raisedAt) < corte.desde) return false;
    const tomable = puedeTomar && x.status === "UNASSIGNED";
    if (f.accion === "tomar" && !tomable) return false;
    if (f.accion === "ninguna" && tomable) return false;
    return true;
  });
  const pag = paginar(filtradas);

  const tomar = async (a: Alerta) => {
    setTomando(a.id);
    try {
      const nueva = await tomarAlerta(a.id);
      setAviso({ tipo: "ok", texto: `Alerta de la cuenta ${nueva.account} asignada a ${nueva.assigneeName ?? usuario.fullName}.` });
    } catch (e) {
      setAviso({ tipo: "error", texto: mensajeAlTomar(e) });
    } finally {
      setTomando(null);
    }
  };
  const seleccionada = lista.find((x) => x.id === selId);
  const cerrarPreview = useCallback(() => setSelId(""), []);

  // ¿Solo está activo el filtro rápido, sin ningún otro filtro?
  const soloTab = (Object.keys(FILTROS_INICIALES) as (keyof typeof FILTROS_INICIALES)[]).every(
    (k) => k === "tab" || f[k] === FILTROS_INICIALES[k],
  );
  let vacioTitulo = "No hay alertas con esos filtros";
  let vacioTexto = "Cambia o quita los filtros para ver más alertas.";
  if (f.tab === "mias" && soloTab) {
    vacioTitulo = "No tienes alertas asignadas";
    vacioTexto = puedeTomar ? "Revisa «Sin asignar» para tomar una." : "Tu rol puede consultar la bandeja, pero no tomar alertas.";
  } else if (f.tab === "mias") {
    vacioTitulo = "No tienes alertas con esos filtros";
  }

  const tabs = [
    { key: "todas", label: "Todas", count: lista.length },
    { key: "mias", label: "Mis alertas", count: mias },
    { key: "sin", label: "Sin asignar", count: sinAsignar },
  ];

  return (
    <div className="flex min-w-0 flex-col gap-5">
      <Encabezado titulo="Bandeja de alertas">
        Ordenadas por criticidad, de la más urgente a la menos urgente. {NOTA_HORA}
      </Encabezado>

      <div className="flex flex-wrap gap-2.5" role="group" aria-label="Filtro rápido">
        {tabs.map((t) => (
          <button
            key={t.key}
            type="button"
            className="tab"
            aria-pressed={f.tab === t.key}
            onClick={() => setFiltro("tab", t.key)}
          >
            {t.label}
            <span key={t.count} className="anim-rebote font-extrabold">
              {t.count}
            </span>
          </button>
        ))}
      </div>

      <PanelFiltros etiqueta="alertas" hayFiltros={hayFiltros} onLimpiar={limpiar}>
        <FiltroSelect
          id="a-c"
          label="Criticidad"
          value={f.crit}
          onChange={(v) => setFiltro("crit", v)}
          opciones={[
            { value: "todas", label: "Todas" },
            { value: "CRITICAL", label: "Crítica" },
            { value: "HIGH", label: "Alta" },
            { value: "MEDIUM", label: "Media" },
            { value: "LOW", label: "Baja" },
          ]}
        />
        <FiltroTexto
          id="a-q"
          label="Cuenta"
          placeholder="Por ejemplo, 4821"
          inputMode="numeric"
          value={f.cuenta}
          onChange={(v) => setFiltro("cuenta", v)}
        />
        <FiltroTexto
          id="a-m"
          label="Motivo"
          placeholder="Por ejemplo, dispositivo"
          value={f.motivo}
          onChange={(v) => setFiltro("motivo", v)}
        />
        <FiltroSelect
          id="a-e"
          label="Estado"
          value={f.estado}
          onChange={(v) => setFiltro("estado", v)}
          opciones={[
            { value: "todos", label: "Todos" },
            { value: "UNASSIGNED", label: "Sin asignar" },
            { value: "ASSIGNED", label: "Asignada" },
            { value: "IN_REVIEW", label: "En revisión" },
          ]}
        />
        <FiltroSelect
          id="a-r"
          label="Responsable"
          value={f.resp}
          onChange={(v) => setFiltro("resp", v)}
          opciones={[
            { value: "todos", label: "Todos" },
            ...responsables.map((x) => ({ value: x, label: x })),
            { value: SIN_RESPONSABLE, label: "Sin responsable" },
          ]}
        />
        <FiltroSelect
          id="a-f"
          label="Fecha"
          value={f.fecha}
          onChange={(v) => {
            setCorte(corteDe(v));
            setFiltro("fecha", v);
          }}
          opciones={[
            { value: "todas", label: "Todas las fechas" },
            { value: "7d", label: "Últimos 7 días" },
            { value: "48h", label: "Últimas 48 horas" },
            { value: "hoy", label: "Hoy" },
          ]}
        />
        <FiltroSelect
          id="a-a"
          label="Acción"
          value={f.accion}
          onChange={(v) => setFiltro("accion", v)}
          opciones={[
            { value: "todas", label: "Todas" },
            { value: "tomar", label: "Se puede tomar" },
            { value: "ninguna", label: "Sin acción disponible" },
          ]}
        />
      </PanelFiltros>

      {aviso && (
        <div
          key={aviso.texto}
          role={aviso.tipo === "ok" ? "status" : "alert"}
          className={`anim-aviso flex items-start justify-between gap-3 border px-4 py-3 text-[15px] leading-[22px] font-semibold ${
            aviso.tipo === "ok"
              ? "border-[#0b6b4a] bg-[#e2f5ec] text-[#0b4f37]"
              : "border-danger bg-[#fbeaea] text-danger"
          }`}
        >
          <span>
            {aviso.tipo === "ok" ? "✓ " : "⚠ "}
            {aviso.texto}
          </span>
          <button
            type="button"
            className="-my-1 flex-none rounded-full px-2 text-lg leading-none hover:bg-black/5"
            aria-label="Cerrar aviso"
            onClick={() => setAviso(null)}
          >
            ×
          </button>
        </div>
      )}

      <div className="flex min-w-0 flex-wrap items-start gap-5">
        <div className="flex min-w-0 flex-[1_1_560px] flex-col gap-4">
          <div className="tblwrap" role="table" aria-label="Alertas">
            <div className="gh g-ale" role="row">
              <div role="columnheader">Criticidad</div>
              <div role="columnheader">Cuenta</div>
              <div role="columnheader">Motivo</div>
              <div role="columnheader">Estado</div>
              <div role="columnheader">Responsable</div>
              <div role="columnheader">Fecha</div>
              <div role="columnheader">Acción</div>
            </div>

            {alertas.cargando && (
              <Cargando
                etiqueta="alertas"
                className="md:min-w-[1030px]"
                grid="g-ale"
                anchos={["90px", "70px", "75%", "90px", "100px", "70px", "70px"]}
              />
            )}
            {alertas.error && (
              <ErrorCarga
                titulo="No pudimos cargar las alertas"
                texto={mensajeDeError(alertas.error, "Hubo un problema con el servidor. Intenta de nuevo en un momento.")}
                onRetry={alertas.recargar}
              />
            )}
            {alertas.datos && lista.length === 0 && (
              <Vacio
                titulo="Tu bandeja está al día"
                texto="No hay alertas por atender en este momento. Cuando el sistema genere una nueva, aparecerá aquí."
              />
            )}
            {lista.length > 0 && filtradas.length === 0 && (
              <Vacio titulo={vacioTitulo} texto={vacioTexto} onLimpiar={hayFiltros ? limpiar : undefined} />
            )}
            {pag.visibles.map((a, i) => (
              <FilaAlerta
                key={a.id}
                indice={i}
                alerta={a}
                puedeTomar={puedeTomar}
                tomando={tomando === a.id}
                onOpen={() => setSelId(a.id)}
                onTake={() => tomar(a)}
              />
            ))}
          </div>

          {filtradas.length > 0 && (
            <Paginacion
              etiqueta="alertas"
              total={pag.total}
              pagina={pag.pagina}
              totalPaginas={pag.totalPaginas}
              tamano={tamano}
              onPagina={setPagina}
              onTamano={setTamano}
            />
          )}
        </div>

        {seleccionada && (
          <VistaPrevia
            alerta={seleccionada}
            puedeTomar={puedeTomar}
            tomando={tomando === seleccionada.id}
            onClose={cerrarPreview}
            onTake={() => tomar(seleccionada)}
          />
        )}
      </div>
    </div>
  );
}

function insignias(alerta: Alerta) {
  const crit = CRITICIDAD[alerta.criticality];
  const est = ESTADO_ALERTA[alerta.status];
  return {
    crit: <span className={`badge ${crit.cls}`}>{crit.label}</span>,
    est: <span className={`badge ${est.cls}`}>{est.label}</span>,
  };
}

function BotonTomar({
  alerta: a,
  tomando,
  onTake,
  className = "",
}: {
  alerta: Alerta;
  tomando: boolean;
  onTake: () => void;
  className?: string;
}) {
  return (
    <button
      type="button"
      className={`btn ${className}`}
      onClick={onTake}
      disabled={tomando}
      aria-label={`Tomar la alerta de la cuenta ${a.account}`}
    >
      {tomando ? "Tomando…" : "Tomarla"}
    </button>
  );
}

function FilaAlerta({
  alerta: a,
  indice,
  puedeTomar,
  tomando,
  onOpen,
  onTake,
}: {
  alerta: Alerta;
  indice: number;
  puedeTomar: boolean;
  tomando: boolean;
  onOpen: () => void;
  onTake: () => void;
}) {
  const b = insignias(a);
  return (
    <div className="gr g-ale anim-fila" style={{ "--i": indice } as CSSProperties} role="row">
      <div role="cell">
        <button type="button" className="rowlink" onClick={onOpen} aria-label={`Ver vista previa: ${a.reason}`}>
          {b.crit}
        </button>
      </div>
      <div role="cell" data-label="Cuenta" className="tabular-nums">
        <span className="clip2" title={a.account}>
          {a.account}
        </span>
      </div>
      <div role="cell">
        <span className="clip2" title={a.reason}>
          {a.reason}
        </span>
      </div>
      <div role="cell" data-label="Estado">
        {b.est}
      </div>
      <div role="cell" data-label="Responsable">
        <span className="clip2" title={a.assigneeName || undefined}>
          {a.assigneeName || "—"}
        </span>
      </div>
      <div role="cell" data-label="Fecha" className="text-ink-soft">
        {fechaCorta(a.raisedAt)}
      </div>
      <div role="cell" className="empty:hidden">
        {puedeTomar && a.status === "UNASSIGNED" && (
          <BotonTomar alerta={a} tomando={tomando} onTake={onTake} className="btn-take" />
        )}
      </div>
    </div>
  );
}

function VistaPrevia({
  alerta: a,
  puedeTomar,
  tomando,
  onClose,
  onTake,
}: {
  alerta: Alerta;
  puedeTomar: boolean;
  tomando: boolean;
  onClose: () => void;
  onTake: () => void;
}) {
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
        <div className="text-base leading-6 font-semibold">{a.reason}</div>
        <KeyValue
          className="grid-cols-[100px_1fr]"
          items={[
            { k: "Cuenta", v: a.account },
            { k: "Responsable", v: a.assigneeName || "—" },
            { k: "Fecha", v: fechaCorta(a.raisedAt) },
          ]}
        />
        <div className="border border-dashed border-line-strong bg-soft px-4 py-3.5 text-sm leading-5 text-ink-soft">
          <strong className="text-ink">Detalle: próximamente.</strong> En esta versión solo puedes ver el resumen y tomar
          alertas sin dueño.
        </div>
        {puedeTomar && a.status === "UNASSIGNED" && <BotonTomar alerta={a} tomando={tomando} onTake={onTake} />}
      </aside>
    </>
  );
}
