"use client";

import type { ReactNode } from "react";
import { PAGE_SIZES } from "@/lib/console/hooks";

export type Pair = { k: string; v: ReactNode };

export function PageHeading({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div>
      <h1 className="mt-0 mb-1 font-display text-[28px] font-extrabold">{title}</h1>
      <p className="m-0 text-[15px] leading-[22px] text-muted">{children}</p>
    </div>
  );
}

export function KeyValue({ items, className = "" }: { items: Pair[]; className?: string }) {
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

export function Loading({ label, widths, grid, className = "" }: { label: string; widths: string[]; grid: string; className?: string }) {
  return (
    <div role="status" aria-label={`Cargando ${label}`} className={className}>
      {[1, 2, 3, 4, 5, 6].map((i) => (
        <div key={i} className={`gr ${grid} min-w-0`}>
          {widths.map((w, j) => (
            <div key={j} className="skel" style={{ width: w }} />
          ))}
        </div>
      ))}
      <div className="p-4 text-sm text-muted">Cargando {label}…</div>
    </div>
  );
}

export function LoadError({ title, text, onRetry }: { title: string; text: string; onRetry: () => void }) {
  return (
    <div role="alert" className="flex min-w-0 flex-col items-start gap-3 px-6 py-9">
      <div className="font-display text-xl font-extrabold text-danger">{title}</div>
      <div className="max-w-[520px] text-[15px] leading-[22px]">{text}</div>
      <button type="button" className="btn" onClick={onRetry}>
        Reintentar
      </button>
    </div>
  );
}

export function Empty({ title, text, onClear }: { title: string; text: string; onClear?: () => void }) {
  return (
    <div className="flex min-w-0 flex-col items-start gap-2 px-6 py-11">
      <div className="font-display text-xl font-extrabold">{title}</div>
      <div className="max-w-[520px] text-[15px] leading-[22px] text-muted">{text}</div>
      {onClear && (
        <button type="button" className="btn2 mt-2" onClick={onClear}>
          Quitar filtros
        </button>
      )}
    </div>
  );
}

type Option = { value: string; label: string };

export function TextFilter({
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

export function SelectFilter({
  id,
  label,
  value,
  onChange,
  options,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (v: string) => void;
  options: Option[];
}) {
  return (
    <div className="min-w-0">
      <label htmlFor={id} className="lbl">
        {label}
      </label>
      <select id={id} className="fld" value={value} onChange={(e) => onChange(e.target.value)}>
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </div>
  );
}

/** One field per table column, in a grid that adapts to the width. */
export function FiltersPanel({
  label,
  hasFilters,
  onClear,
  children,
}: {
  label: string;
  hasFilters: boolean;
  onClear: () => void;
  children: ReactNode;
}) {
  return (
    <section aria-label={`Filtros de ${label}`} className="flex flex-col gap-3">
      <div className="grid grid-cols-1 gap-x-6 gap-y-4 sm:grid-cols-2 lg:grid-cols-4">{children}</div>
      {hasFilters && (
        <div className="anim-fade">
          <button type="button" className="btn2 min-h-9 px-4 text-[13px]" onClick={onClear}>
            Quitar filtros
          </button>
        </div>
      )}
    </section>
  );
}

/** Page numbers to show, with "…" when there are many. */
function pageNumbers(current: number, total: number): (number | "…")[] {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1);
  const near = [1, current - 1, current, current + 1, total].filter((n) => n >= 1 && n <= total);
  const unique = [...new Set(near)].sort((a, b) => a - b);
  const output: (number | "…")[] = [];
  unique.forEach((n, i) => {
    if (i > 0 && n - unique[i - 1] > 1) output.push("…");
    output.push(n);
  });
  return output;
}

export function Pagination({
  label,
  total,
  page,
  totalPages,
  size,
  onPage,
  onSize,
}: {
  label: string;
  total: number;
  page: number;
  totalPages: number;
  size: number;
  onPage: (n: number) => void;
  onSize: (n: number) => void;
}) {
  const firstItem = total === 0 ? 0 : (page - 1) * size + 1;
  const lastItem = Math.min(page * size, total);
  const id = `tam-${label.replace(/\s+/g, "-")}`;

  return (
    <nav
      aria-label={`Paginación de ${label}`}
      className="flex flex-wrap items-center justify-between gap-x-6 gap-y-3 text-sm"
    >
      <p className="m-0 text-muted" aria-live="polite">
        Mostrando{" "}
        <strong className="text-ink">
          {firstItem}–{lastItem}
        </strong>{" "}
        de {total} {label}
      </p>
      <div className="flex flex-wrap items-center gap-x-6 gap-y-3">
        <div className="flex items-center gap-2">
          <label htmlFor={id} className="text-muted">
            Filas por página
          </label>
          <select id={id} className="fld h-9 w-auto pr-1" value={size} onChange={(e) => onSize(Number(e.target.value))}>
            {PAGE_SIZES.map((n) => (
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
            disabled={page <= 1}
            onClick={() => onPage(page - 1)}
          >
            ‹
          </button>
          {pageNumbers(page, totalPages).map((n, i) =>
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
                aria-current={n === page ? "page" : undefined}
                onClick={() => onPage(n)}
              >
                {n}
              </button>
            ),
          )}
          <button
            type="button"
            className="pag"
            aria-label="Página siguiente"
            disabled={page >= totalPages}
            onClick={() => onPage(page + 1)}
          >
            ›
          </button>
        </div>
      </div>
    </nav>
  );
}
