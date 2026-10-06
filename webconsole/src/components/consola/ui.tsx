"use client";

import { useSearchParams } from "next/navigation";
import { useState, type ReactNode } from "react";
import type { EstadoDatos, Par } from "@/lib/consola/datos";

const ESTADOS: EstadoDatos[] = ["normal", "cargando", "error", "vacio"];

/**
 * Estado de los datos de la pantalla. Mientras no haya backend se puede forzar
 * con `?estado=cargando|error|vacio` para revisar esos diseños. «Reintentar»
 * vuelve al estado normal.
 */
export function useEstadoDatos() {
  const param = useSearchParams().get("estado") as EstadoDatos | null;
  const [reintentado, setReintentado] = useState(false);
  const estado: EstadoDatos = !reintentado && param && ESTADOS.includes(param) ? param : "normal";
  return { estado, reintentar: () => setReintentado(true) };
}

export function Encabezado({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <div>
      <h1 className="mt-0 mb-1 font-display text-[28px] font-extrabold">{titulo}</h1>
      <p className="m-0 text-[15px] leading-[22px] text-muted">{children}</p>
    </div>
  );
}

export function KeyValue({ items, className = "" }: { items: Par[]; className?: string }) {
  return (
    <dl className={`kv m-0 ${className}`}>
      {items.map((x) => (
        <div key={x.k} className="contents">
          <dt className="k">{x.k}</dt>
          <dd className="v m-0">{x.v}</dd>
        </div>
      ))}
    </dl>
  );
}

export function Cargando({ etiqueta, anchos, grid, className = "" }: { etiqueta: string; anchos: string[]; grid: string; className?: string }) {
  return (
    <div role="status" aria-label={`Cargando ${etiqueta}`} className={className}>
      {[1, 2, 3, 4, 5, 6].map((i) => (
        <div key={i} className={`gr ${grid} min-w-0`}>
          {anchos.map((w, j) => (
            <div key={j} className="skel" style={{ width: w }} />
          ))}
        </div>
      ))}
      <div className="p-4 text-sm text-muted">Cargando {etiqueta}…</div>
    </div>
  );
}

export function ErrorCarga({ titulo, texto, onRetry }: { titulo: string; texto: string; onRetry: () => void }) {
  return (
    <div role="alert" className="flex min-w-0 flex-col items-start gap-3 px-6 py-9">
      <div className="font-display text-xl font-extrabold text-danger">{titulo}</div>
      <div className="max-w-[520px] text-[15px] leading-[22px]">{texto}</div>
      <button type="button" className="btn" onClick={onRetry}>
        Reintentar
      </button>
    </div>
  );
}

export function Vacio({ titulo, texto, onLimpiar }: { titulo: string; texto: string; onLimpiar?: () => void }) {
  return (
    <div className="flex min-w-0 flex-col items-start gap-2 px-6 py-11">
      <div className="font-display text-xl font-extrabold">{titulo}</div>
      <div className="max-w-[520px] text-[15px] leading-[22px] text-muted">{texto}</div>
      {onLimpiar && (
        <button type="button" className="btn2 mt-2" onClick={onLimpiar}>
          Quitar filtros
        </button>
      )}
    </div>
  );
}

// ---- Filtros y paginación de tablas

export const TAMANOS_PAGINA = [5, 10, 20];

/**
 * Estado de filtros y paginación de una tabla. Cambiar cualquier filtro o el
 * tamaño de página vuelve a la primera página.
 */
export function useFiltrosPaginados<F extends Record<string, string>>(inicial: F, tamanoInicial = TAMANOS_PAGINA[0]) {
  const [filtros, setFiltros] = useState<F>(inicial);
  const [pagina, setPagina] = useState(1);
  const [tamano, setTamanoEstado] = useState(tamanoInicial);

  const setFiltro = (k: keyof F, v: string) => {
    setFiltros((f) => ({ ...f, [k]: v }));
    setPagina(1);
  };
  const limpiar = () => {
    setFiltros(inicial);
    setPagina(1);
  };
  const setTamano = (n: number) => {
    setTamanoEstado(n);
    setPagina(1);
  };
  const hayFiltros = (Object.keys(inicial) as (keyof F)[]).some((k) => filtros[k] !== inicial[k]);

  function paginar<T>(items: T[]) {
    const totalPaginas = Math.max(1, Math.ceil(items.length / tamano));
    const actual = Math.min(pagina, totalPaginas);
    return {
      visibles: items.slice((actual - 1) * tamano, actual * tamano),
      pagina: actual,
      totalPaginas,
      total: items.length,
    };
  }

  return { filtros, setFiltro, limpiar, hayFiltros, tamano, setTamano, setPagina, paginar };
}

type Opcion = { value: string; label: string };

export function FiltroTexto({
  id,
  label,
  value,
  onChange,
  placeholder,
  inputMode,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
  inputMode?: "numeric";
}) {
  return (
    <div className="min-w-0">
      <label htmlFor={id} className="lbl">
        {label}
      </label>
      <input
        id={id}
        className="fld"
        type="search"
        inputMode={inputMode}
        placeholder={placeholder}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
    </div>
  );
}

export function FiltroSelect({
  id,
  label,
  value,
  onChange,
  opciones,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (v: string) => void;
  opciones: Opcion[];
}) {
  return (
    <div className="min-w-0">
      <label htmlFor={id} className="lbl">
        {label}
      </label>
      <select id={id} className="fld" value={value} onChange={(e) => onChange(e.target.value)}>
        {opciones.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </div>
  );
}

/** Un campo por columna de la tabla, en una cuadrícula que se adapta al ancho. */
export function PanelFiltros({
  etiqueta,
  hayFiltros,
  onLimpiar,
  children,
}: {
  etiqueta: string;
  hayFiltros: boolean;
  onLimpiar: () => void;
  children: ReactNode;
}) {
  return (
    <section aria-label={`Filtros de ${etiqueta}`} className="flex flex-col gap-3">
      <div className="grid grid-cols-1 gap-x-6 gap-y-4 sm:grid-cols-2 lg:grid-cols-4">{children}</div>
      {hayFiltros && (
        <div className="anim-aparecer">
          <button type="button" className="btn2 min-h-9 px-4 text-[13px]" onClick={onLimpiar}>
            Quitar filtros
          </button>
        </div>
      )}
    </section>
  );
}

/** Números de página a mostrar, con «…» cuando hay muchas. */
function numerosDePagina(actual: number, total: number): (number | "…")[] {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1);
  const cerca = [1, actual - 1, actual, actual + 1, total].filter((n) => n >= 1 && n <= total);
  const unicos = [...new Set(cerca)].sort((a, b) => a - b);
  const salida: (number | "…")[] = [];
  unicos.forEach((n, i) => {
    if (i > 0 && n - unicos[i - 1] > 1) salida.push("…");
    salida.push(n);
  });
  return salida;
}

export function Paginacion({
  etiqueta,
  total,
  pagina,
  totalPaginas,
  tamano,
  onPagina,
  onTamano,
}: {
  etiqueta: string;
  total: number;
  pagina: number;
  totalPaginas: number;
  tamano: number;
  onPagina: (n: number) => void;
  onTamano: (n: number) => void;
}) {
  const desde = total === 0 ? 0 : (pagina - 1) * tamano + 1;
  const hasta = Math.min(pagina * tamano, total);
  const id = `tam-${etiqueta.replace(/\s+/g, "-")}`;

  return (
    <nav
      aria-label={`Paginación de ${etiqueta}`}
      className="flex flex-wrap items-center justify-between gap-x-6 gap-y-3 text-sm"
    >
      <p className="m-0 text-muted" aria-live="polite">
        Mostrando{" "}
        <strong className="text-ink">
          {desde}–{hasta}
        </strong>{" "}
        de {total} {etiqueta}
      </p>
      <div className="flex flex-wrap items-center gap-x-6 gap-y-3">
        <div className="flex items-center gap-2">
          <label htmlFor={id} className="text-muted">
            Filas por página
          </label>
          <select id={id} className="fld h-9 w-auto pr-1" value={tamano} onChange={(e) => onTamano(Number(e.target.value))}>
            {TAMANOS_PAGINA.map((n) => (
              <option key={n} value={n}>
                {n}
              </option>
            ))}
          </select>
        </div>
        <div className="flex items-center gap-1.5">
          <button
            type="button"
            className="pag"
            aria-label="Página anterior"
            disabled={pagina <= 1}
            onClick={() => onPagina(pagina - 1)}
          >
            ‹
          </button>
          {numerosDePagina(pagina, totalPaginas).map((n, i) =>
            n === "…" ? (
              <span key={`e${i}`} className="px-1 text-muted" aria-hidden="true">
                …
              </span>
            ) : (
              <button
                key={n}
                type="button"
                className="pag"
                aria-label={`Página ${n}`}
                aria-current={n === pagina ? "page" : undefined}
                onClick={() => onPagina(n)}
              >
                {n}
              </button>
            ),
          )}
          <button
            type="button"
            className="pag"
            aria-label="Página siguiente"
            disabled={pagina >= totalPaginas}
            onClick={() => onPagina(pagina + 1)}
          >
            ›
          </button>
        </div>
      </div>
    </nav>
  );
}
