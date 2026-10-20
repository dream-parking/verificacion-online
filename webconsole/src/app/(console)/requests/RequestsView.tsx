"use client";

import Link from "next/link";
import type { CSSProperties } from "react";
import {
  Loading,
  PageHeading,
  LoadError,
  SelectFilter,
  TextFilter,
  Pagination,
  FiltersPanel,
  Empty,
} from "@/components/console/Ui";
import { useLoad } from "@/lib/console/hooks";
import { errorMessage, usePaginatedFilters } from "@/lib/console/hooks";
import { apiFetch, fetchAllRequests, type Catalogs, type RequestSummary } from "@/lib/console/api";
import { REQUEST_STATUS, formatDate, formatShortDate, TIME_NOTE, RISK } from "@/lib/console/format";
import { customerNumberOf, fetchCustomerRequestIds, isPartialCustomerNumber } from "@/lib/console/customers";

const INITIAL_FILTERS = {
  requestNumber: "",
  name: "",
  date: "all",
  kind: "all",
  amount: "all",
  risk: "all",
  status: "all",
};

const UNDECLARED = "__none__";
const NO_NUMBER = "Sin número aún";

/**
 * `customerRequests` are the requests of the customer whose number was typed in the first filter (VDI-123): the API
 * list does not carry the customer number. It is undefined while they load or when no customer has that number.
 */
function matchesFilters(r: RequestSummary, f: typeof INITIAL_FILTERS, customerRequests?: string[] | null) {
  const requestNumber = f.requestNumber.trim().toLowerCase();
  const name = f.name.trim().toLowerCase();
  if (customerNumberOf(f.requestNumber)) {
    if (!customerRequests?.includes(r.id)) return false;
  } else if (requestNumber && !(r.number ?? NO_NUMBER).toLowerCase().includes(requestNumber)) return false;
  if (name && !(r.name ?? "").toLowerCase().includes(name)) return false;
  if (f.date !== "all" && formatDate(r.date) !== f.date) return false;
  if (f.kind === UNDECLARED ? r.transactionTypeLabel : f.kind !== "all" && r.transactionTypeLabel !== f.kind)
    return false;
  if (
    f.amount === UNDECLARED
      ? r.monthlyAmountRangeLabel
      : f.amount !== "all" && r.monthlyAmountRangeLabel !== f.amount
  )
    return false;
  if (f.risk !== "all" && r.riskLevel !== f.risk) return false;
  if (f.status !== "all" && r.status !== f.status) return false;
  return true;
}

/** Options of a filter: the catalog ones plus any value in the data that is not in the catalog. */
function optionsFor(catalog: string[] | undefined, values: (string | null)[]) {
  const all = [...(catalog ?? [])];
  for (const v of values) if (v && !all.includes(v)) all.push(v);
  return all.map((x) => ({ value: x, label: x }));
}

export function RequestsView() {
  const requests = useLoad("solicitudes", fetchAllRequests);
  // The catalogs only provide the filter options; if they fail, the values found in the data are used.
  const catalogs = useLoad("catalogos", (t) => apiFetch<Catalogs>("/api/catalogs", { token: t }));
  const { filters: f, setFilter, clear, hasFilters, size, setSize, setPage, paginate } =
    usePaginatedFilters(INITIAL_FILTERS);
  const customerNumber = customerNumberOf(f.requestNumber);
  const customer = useLoad(customerNumber && `cliente:${customerNumber}`, (t) =>
    fetchCustomerRequestIds(t, customerNumber!),
  );

  const data = requests.data ?? [];
  const filtered = data.filter((r) => matchesFilters(r, f, customer.data));
  const pagination = paginate(filtered);
  const dates = [...new Set(data.map((r) => formatDate(r.date)))];

  return (
    <div className="flex min-w-0 flex-col gap-5">
      <PageHeading title="Solicitudes">
        {requests.data
          ? `${filtered.length} de ${data.length} solicitudes de onboarding. ${TIME_NOTE}`
          : `Solicitudes de onboarding recibidas desde la app móvil. ${TIME_NOTE}`}
      </PageHeading>

      <FiltersPanel label="solicitudes" hasFilters={hasFilters} onClear={clear}>
        <TextFilter
          id="f-num"
          label="Solicitud o cliente"
          placeholder="SOL-2026-00418 o CLI-0000001"
          value={f.requestNumber}
          onChange={(v) => setFilter("requestNumber", v)}
        />
        <TextFilter
          id="f-nom"
          label="Nombre"
          placeholder="Por ejemplo, Rivas"
          value={f.name}
          onChange={(v) => setFilter("name", v)}
        />
        <SelectFilter
          id="f-fecha"
          label="Fecha"
          value={f.date}
          onChange={(v) => setFilter("date", v)}
          options={[{ value: "all", label: "Todas" }, ...dates.map((x) => ({ value: x, label: x }))]}
        />
        <SelectFilter
          id="f-tipo"
          label="Tipo de dinero"
          value={f.kind}
          onChange={(v) => setFilter("kind", v)}
          options={[
            { value: "all", label: "Todos" },
            ...optionsFor(
              catalogs.data?.transactionTypes.map((x) => x.label),
              data.map((r) => r.transactionTypeLabel),
            ),
            { value: UNDECLARED, label: "Sin declarar" },
          ]}
        />
        <SelectFilter
          id="f-monto"
          label="Monto mensual"
          value={f.amount}
          onChange={(v) => setFilter("amount", v)}
          options={[
            { value: "all", label: "Todos" },
            ...optionsFor(
              catalogs.data?.monthlyAmountRanges.map((x) => x.label),
              data.map((r) => r.monthlyAmountRangeLabel),
            ),
            { value: UNDECLARED, label: "Sin declarar" },
          ]}
        />
        <SelectFilter
          id="f-r"
          label="Nivel de riesgo"
          value={f.risk}
          onChange={(v) => setFilter("risk", v)}
          options={[
            { value: "all", label: "Todos" },
            { value: "LOW", label: "Bajo" },
            { value: "MEDIUM", label: "Medio" },
            { value: "HIGH", label: "Alto" },
            { value: "PENDING_REVIEW", label: "Pendiente de evaluación" },
            { value: "NOT_EVALUATED", label: "Sin evaluar" },
          ]}
        />
        <SelectFilter
          id="f-e"
          label="Estado"
          value={f.status}
          onChange={(v) => setFilter("status", v)}
          options={[
            { value: "all", label: "Todos" },
            { value: "IN_PROGRESS", label: "En progreso" },
            { value: "COMPLETED", label: "Completada" },
            { value: "ENHANCED_REVIEW", label: "Revisión reforzada" },
            { value: "AWAITING_RNPN", label: "En espera" },
            { value: "ABANDONED", label: "Abandonada" },
          ]}
        />
      </FiltersPanel>

      <div className="tblwrap" role="table" aria-label="Solicitudes de onboarding">
        <div className="gh g-requests" role="row">
          <div role="columnheader">Solicitud</div>
          <div role="columnheader">Nombre</div>
          <div role="columnheader">Fecha</div>
          <div role="columnheader">Tipo de dinero</div>
          <div role="columnheader">Monto mensual</div>
          <div role="columnheader">Nivel de riesgo</div>
          <div role="columnheader">Score</div>
          <div role="columnheader">Estado</div>
        </div>

        {requests.loading && (
          <Loading
            label="solicitudes"
            className="md:min-w-[1190px]"
            grid="g-requests"
            widths={["110px", "70%", "90px", "110px", "60px", "120px", "40px", "90px"]}
          />
        )}
        {requests.error && (
          <LoadError
            title="No pudimos cargar la información"
            text={errorMessage(
              requests.error,
              "Hubo un problema con el servidor. Tus datos no se han perdido; intenta de nuevo.",
            )}
            onRetry={requests.reload}
          />
        )}
        {requests.data && data.length === 0 && (
          <Empty
            title="Todavía no hay solicitudes"
            text="Cuando alguien envíe su solicitud desde la app, aparecerá aquí."
          />
        )}
        {data.length > 0 && customer.loading && (
          <div role="status" className="p-4 text-sm text-muted">
            Buscando las solicitudes del cliente {customerNumber}…
          </div>
        )}
        {data.length > 0 && customer.error && (
          <LoadError
            title="No pudimos buscar el número de cliente"
            text={errorMessage(customer.error, "Hubo un problema con el servidor. Intenta de nuevo en un momento.")}
            onRetry={customer.reload}
          />
        )}
        {data.length > 0 && filtered.length === 0 && !customer.loading && !customer.error && (
          <Empty
            title="No encontramos solicitudes"
            text={
              customerNumber && customer.data === null
                ? `No hay ningún cliente con el número ${customerNumber}.`
                : isPartialCustomerNumber(f.requestNumber)
                  ? "Escribe el número de cliente completo, por ejemplo CLI-0000001."
                  : "Prueba con otros filtros, o quítalos para ver todas."
            }
            onClear={hasFilters ? clear : undefined}
          />
        )}
        {pagination.visibleItems.map((r, i) => {
          const riskBadge = RISK[r.riskLevel];
          const state = REQUEST_STATUS[r.status];
          const name = r.name || "Sin nombre aún";
          return (
            <div key={r.id} className="gr g-requests anim-row" style={{ "--i": i } as CSSProperties} role="row">
              <div role="cell">
                <Link href={`/requests/${r.id}`} className="rowlink underline" aria-label={`Abrir solicitud de ${name}`}>
                  {r.number || NO_NUMBER}
                </Link>
              </div>
              <div role="cell" className={r.name ? "font-semibold" : "text-muted"}>
                <span className="clip2" title={name}>
                  {name}
                </span>
              </div>
              <div role="cell" data-label="Fecha" className="text-ink-soft">
                {formatShortDate(r.date)}
              </div>
              <div role="cell" data-label="Tipo de dinero">
                <span className="clip2" title={r.transactionTypeLabel || undefined}>
                  {r.transactionTypeLabel || "—"}
                </span>
              </div>
              <div role="cell" data-label="Monto mensual" className="font-semibold">
                <span className="clip2" title={r.monthlyAmountRangeLabel || undefined}>
                  {r.monthlyAmountRangeLabel || "—"}
                </span>
              </div>
              <div role="cell" data-label="Nivel de riesgo">
                <span className={`badge ${riskBadge.cls}`}>{riskBadge.label}</span>
              </div>
              <div role="cell" data-label="Score" className="font-semibold">
                {r.score != null ? (
                  <>
                    {r.score}
                    <span className="font-normal text-muted"> / 100</span>
                  </>
                ) : (
                  <span className="font-normal text-muted" title="Sin score del modelo">
                    —
                  </span>
                )}
              </div>
              <div role="cell" data-label="Estado">
                <span className={`badge ${state.cls}`}>{state.label}</span>
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
