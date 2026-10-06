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

export function Cargando({ etiqueta, minWidth, anchos, grid }: { etiqueta: string; minWidth: number; anchos: string[]; grid: string }) {
  return (
    <div role="status" aria-label={`Cargando ${etiqueta}`} style={{ minWidth }}>
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
