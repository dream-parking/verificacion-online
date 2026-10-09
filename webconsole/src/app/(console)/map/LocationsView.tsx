"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useMemo, useState, type CSSProperties } from "react";
import {
  Empty,
  FiltersPanel,
  Loading,
  LoadError,
  PageHeading,
  Pagination,
  SelectFilter,
  TextFilter,
} from "@/components/console/Ui";
import { errorMessage, useLoad, usePaginatedFilters } from "@/lib/console/hooks";
import { RISK } from "@/lib/console/format";
import {
  fetchLocations,
  groupByPlace,
  groupKey,
  RISK_MARKER,
  type LocatedRequest,
} from "@/lib/console/locations";
import type { RiskLevel } from "@/lib/console/api";
import { LocationsMap, type MapFocus } from "./LocationsMap";

const INITIAL_FILTERS = { text: "", location: "all" };

const LEGEND: RiskLevel[] = ["LOW", "PENDING_REVIEW", "MEDIUM", "HIGH", "NOT_EVALUATED"];

function matches(r: LocatedRequest, f: typeof INITIAL_FILTERS) {
  const text = f.text.trim().toLowerCase();
  if (text && !`${r.name ?? ""} ${r.requestNumber ?? ""} ${r.ip ?? ""} ${r.place ?? ""}`.toLowerCase().includes(text)) {
    return false;
  }
  if (f.location === "located" && !r.located) return false;
  if (f.location === "unlocated" && r.located) return false;
  return true;
}

export function LocationsView() {
  const router = useRouter();
  const load = useLoad("locations", fetchLocations);
  const { filters: f, setFilter, clear, hasFilters, size, setSize, setPage, paginate } =
    usePaginatedFilters(INITIAL_FILTERS);
  const [focus, setFocus] = useState<MapFocus>(null);

  const items = useMemo(() => load.data ?? [], [load.data]);
  const groups = useMemo(() => groupByPlace(items), [items]);
  const located = items.filter((r) => r.located).length;
  const filtered = items.filter((r) => matches(r, f));
  const pagination = paginate(filtered);

  const openRequest = useCallback((id: string) => router.push(`/requests/${id}`), [router]);

  return (
    <div className="flex min-w-0 flex-col gap-5">
      <PageHeading title="Mapa de ubicaciones">
        Desde dónde se registró cada solicitud, según su dirección IP. La ubicación es aproximada: puede ser la del
        proveedor de internet y no la de la persona.
      </PageHeading>

      {load.error ? (
        <div className="card p-0!">
          <LoadError
            title="No pudimos cargar las ubicaciones"
            text={errorMessage(load.error, "Hubo un problema con el servidor. Intenta de nuevo en un momento.")}
            onRetry={load.reload}
          />
        </div>
      ) : (
        <section aria-label="Mapa" className="flex min-w-0 flex-col gap-3">
          <div className="flex flex-wrap items-center justify-between gap-x-6 gap-y-2">
            <p className="m-0 text-[15px] text-ink-soft" aria-live="polite">
              {load.data ? (
                <>
                  <strong className="text-ink">{located}</strong> de {items.length} solicitudes con ubicación
                  {items.length - located > 0 && ` · ${items.length - located} sin ubicación`}
                </>
              ) : (
                "Cargando ubicaciones…"
              )}
            </p>
            <ul className="m-0 flex list-none flex-wrap gap-x-4 gap-y-1 p-0 text-[13px] text-muted" aria-label="Leyenda: nivel de riesgo">
              {LEGEND.map((level) => (
                <li key={level} className="flex items-center gap-1.5">
                  <span
                    className="inline-block size-3 rounded-full border-2"
                    style={{ background: RISK_MARKER[level].fill, borderColor: RISK_MARKER[level].stroke }}
                    aria-hidden="true"
                  />
                  {RISK[level].label.replace(/^\S+\s/, "")}
                </li>
              ))}
            </ul>
          </div>

          {load.loading && <div className="skel h-[480px]! w-full" role="status" aria-label="Cargando el mapa" />}
          {load.data && groups.length > 0 && <LocationsMap groups={groups} focus={focus} onOpen={openRequest} />}
          {load.data && groups.length === 0 && (
            <div className="card">
              <div className="font-display text-xl font-extrabold">Ninguna solicitud tiene ubicación todavía</div>
              <p className="mt-2 mb-0 max-w-[620px] text-[15px] leading-[22px] text-muted">
                El servidor calcula la ubicación a partir de la IP cuando la app envía las señales. Las solicitudes
                enviadas antes de que existiera ese cálculo no la tienen; las nuevas sí. También queda sin ubicación una
                IP privada o cuando el proveedor no responde.
              </p>
            </div>
          )}
          {load.data && groups.length > 0 && (
            <p className="m-0 text-[13px] leading-[18px] text-muted">
              El tamaño del círculo indica cuántas solicitudes salieron del mismo lugar. Pulsa un círculo para ver quiénes
              son.
            </p>
          )}
        </section>
      )}

      <FiltersPanel label="solicitudes" hasFilters={hasFilters} onClear={clear}>
        <TextFilter
          id="m-q"
          label="Nombre, solicitud, IP o lugar"
          placeholder="Por ejemplo, San Salvador"
          value={f.text}
          onChange={(v) => setFilter("text", v)}
        />
        <SelectFilter
          id="m-u"
          label="Ubicación"
          value={f.location}
          onChange={(v) => setFilter("location", v)}
          options={[
            { value: "all", label: "Todas" },
            { value: "located", label: "Con ubicación" },
            { value: "unlocated", label: "Sin ubicación" },
          ]}
        />
      </FiltersPanel>

      <div className="tblwrap" role="table" aria-label="Ubicación de cada solicitud">
        <div className="gh g-locations" role="row">
          <div role="columnheader">Nombre</div>
          <div role="columnheader">Solicitud</div>
          <div role="columnheader">Dirección IP</div>
          <div role="columnheader">Ubicación aproximada</div>
          <div role="columnheader">Nivel de riesgo</div>
          <div role="columnheader">Mapa</div>
        </div>

        {load.loading && (
          <Loading
            label="ubicaciones"
            className="md:min-w-[1060px]"
            grid="g-locations"
            widths={["70%", "100px", "100px", "70%", "120px", "80px"]}
          />
        )}
        {load.data && items.length === 0 && (
          <Empty title="Todavía no hay solicitudes" text="Cuando alguien envíe su solicitud desde la app, aparecerá aquí." />
        )}
        {items.length > 0 && filtered.length === 0 && (
          <Empty
            title="No encontramos solicitudes"
            text="Prueba con otros filtros, o quítalos para ver todas."
            onClear={hasFilters ? clear : undefined}
          />
        )}
        {pagination.visibleItems.map((r, i) => {
          const risk = RISK[r.riskLevel];
          const name = r.name || "Sin nombre aún";
          return (
            <div key={r.id} className="gr g-locations anim-row" style={{ "--i": i } as CSSProperties} role="row">
              <div role="cell" className="font-semibold">
                <Link href={`/requests/${r.id}`} className="rowlink" aria-label={`Abrir solicitud de ${name}`}>
                  <span className="clip2" title={name}>
                    {name}
                  </span>
                </Link>
              </div>
              <div role="cell" data-label="Solicitud">
                {r.requestNumber ?? "Sin número aún"}
              </div>
              <div role="cell" data-label="Dirección IP" className="tabular-nums">
                {r.ip ?? "—"}
              </div>
              <div role="cell" data-label="Ubicación aproximada">
                <span className={`clip2 ${r.located ? "" : "text-muted"}`} title={r.place ?? undefined}>
                  {r.located ? (r.place ?? "Ubicación sin nombre") : "Ubicación no disponible"}
                </span>
              </div>
              <div role="cell" data-label="Nivel de riesgo">
                <span className={`badge ${risk.cls}`}>{risk.label}</span>
              </div>
              <div role="cell" data-label="Mapa">
                {r.located ? (
                  <button
                    type="button"
                    className="btn2 relative z-[2] min-h-9 px-4 text-[12px]"
                    aria-label={`Ver en el mapa a ${name}`}
                    onClick={() => {
                      setFocus((current) => ({ key: groupKey(r.latitude!, r.longitude!), n: (current?.n ?? 0) + 1 }));
                      document.querySelector('[aria-label^="Mapa con la ubicación"]')?.scrollIntoView({
                        behavior: "smooth",
                        block: "center",
                      });
                    }}
                  >
                    Ver
                  </button>
                ) : (
                  <span className="text-muted">—</span>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {filtered.length > 0 && (
        <Pagination
          label="solicitudes"
          total={pagination.total}
          page={pagination.page}
          totalPages={pagination.totalPages}
          size={size}
          onPage={setPage}
          onSize={setSize}
        />
      )}
    </div>
  );
}
