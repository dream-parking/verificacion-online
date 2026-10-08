"use client";

import { useCallback, useEffect, useRef, useState, type CSSProperties } from "react";
import { useConsole } from "@/components/console/ConsoleProvider";
import {
  Loading,
  PageHeading,
  LoadError,
  SelectFilter,
  TextFilter,
  KeyValue,
  Pagination,
  FiltersPanel,
  Empty,
} from "@/components/console/Ui";
import { errorMessage, usePaginatedFilters } from "@/lib/console/hooks";
import { ApiError, type Alert } from "@/lib/console/api";
import {
  CRITICALITY,
  ALERT_STATUS,
  formatDate,
  formatShortDate,
  TIME_NOTE,
  canTakeAlerts,
} from "@/lib/console/format";

const INITIAL_FILTERS = {
  tab: "all",
  crit: "all",
  account: "",
  reason: "",
  status: "all",
  assignee: "all",
  date: "all",
  action: "all",
};

const NO_ASSIGNEE = "__none__";
const HOUR_MS = 3600_000;

/** Date filter cutoff, computed when the option is chosen (not on every render). */
type Cutoff = { firstItem?: number; day?: string };
function cutoffFor(option: string): Cutoff {
  const now = Date.now();
  if (option === "today") return { day: formatDate(new Date(now).toISOString()) };
  if (option === "48h") return { firstItem: now - 48 * HOUR_MS };
  if (option === "7d") return { firstItem: now - 7 * 24 * HOUR_MS };
  return {};
}

type Notice = { kind: "ok" | "error"; text: string };

function takeErrorMessage(e: unknown) {
  if (e instanceof ApiError) {
    if (e.status === 409) return "Otra persona tomó esta alerta antes que tú, o ya no está abierta. Actualizamos la bandeja.";
    if (e.status === 403) return "Tu rol no puede tomar alertas.";
    if (e.status === 404) return "Esta alerta ya no existe.";
    if (e.status === 0) return "No pudimos conectar con el servidor. Intenta de nuevo.";
  }
  return "No pudimos tomar la alerta. Intenta de nuevo.";
}

export function AlertsView() {
  const { user, alerts, takeAlert } = useConsole();
  const { filters: f, setFilter, clear, hasFilters, size, setSize, setPage, paginate } =
    usePaginatedFilters(INITIAL_FILTERS);
  const [cutoff, setCutoff] = useState<Cutoff>({});
  const [notice, setNotice] = useState<Notice | null>(null);
  const [taking, setTaking] = useState<string | null>(null);
  const [selId, setSelId] = useState("");

  const canTake = canTakeAlerts(user.role);
  const list = alerts.data ?? [];
  const unassigned = list.filter((x) => x.status === "UNASSIGNED").length;
  const mine = list.filter((x) => x.assigneeId === user.id).length;
  const assignees = [...new Set(list.flatMap((x) => (x.assigneeName ? [x.assigneeName] : [])))].sort();
  const digits = f.account.replace(/\D/g, "");
  const reason = f.reason.trim().toLowerCase();

  // The API already returns them from most to least critical, newest first.
  const filtered = list.filter((x) => {
    if (f.tab === "mine" && x.assigneeId !== user.id) return false;
    if (f.tab === "unassigned" && x.status !== "UNASSIGNED") return false;
    if (f.crit !== "all" && x.criticality !== f.crit) return false;
    if (digits && !x.account.replace(/\D/g, "").includes(digits)) return false;
    if (reason && !x.reason.toLowerCase().includes(reason)) return false;
    if (f.status !== "all" && x.status !== f.status) return false;
    if (f.assignee === NO_ASSIGNEE && x.assigneeName) return false;
    if (f.assignee !== "all" && f.assignee !== NO_ASSIGNEE && x.assigneeName !== f.assignee) return false;
    if (f.date !== "all" && cutoff.day && formatDate(x.raisedAt) !== cutoff.day) return false;
    if (f.date !== "all" && cutoff.firstItem && Date.parse(x.raisedAt) < cutoff.firstItem) return false;
    const takeable = canTake && x.status === "UNASSIGNED";
    if (f.action === "take" && !takeable) return false;
    if (f.action === "none" && takeable) return false;
    return true;
  });
  const pagination = paginate(filtered);

  const take = async (a: Alert) => {
    setTaking(a.id);
    try {
      const updated = await takeAlert(a.id);
      setNotice({ kind: "ok", text: `Alerta de la cuenta ${updated.account} asignada a ${updated.assigneeName ?? user.fullName}.` });
    } catch (e) {
      setNotice({ kind: "error", text: takeErrorMessage(e) });
    } finally {
      setTaking(null);
    }
  };
  const selected = list.find((x) => x.id === selId);
  const closePreview = useCallback(() => setSelId(""), []);

  // Is only the quick filter active, with no other filter?
  const onlyTab = (Object.keys(INITIAL_FILTERS) as (keyof typeof INITIAL_FILTERS)[]).every(
    (k) => k === "tab" || f[k] === INITIAL_FILTERS[k],
  );
  let emptyTitle = "No hay alertas con esos filtros";
  let emptyText = "Cambia o quita los filtros para ver más alertas.";
  if (f.tab === "mine" && onlyTab) {
    emptyTitle = "No tienes alertas asignadas";
    emptyText = canTake ? "Revisa «Sin asignar» para tomar una." : "Tu rol puede consultar la bandeja, pero no tomar alertas.";
  } else if (f.tab === "mine") {
    emptyTitle = "No tienes alertas con esos filtros";
  }

  const tabs = [
    { key: "all", label: "Todas", count: list.length },
    { key: "mine", label: "Mis alertas", count: mine },
    { key: "unassigned", label: "Sin asignar", count: unassigned },
  ];

  return (
    <div className="flex min-w-0 flex-col gap-5">
      <PageHeading title="Bandeja de alertas">
        Ordenadas por criticidad, de la más urgente a la menos urgente. {TIME_NOTE}
      </PageHeading>

      <div className="flex flex-wrap gap-2.5" role="group" aria-label="Filtro rápido">
        {tabs.map((t) => (
          <button
            key={t.key}
            type="button"
            className="tab"
            aria-pressed={f.tab === t.key}
            onClick={() => setFilter("tab", t.key)}
          >
            {t.label}
            <span key={t.count} className="anim-bounce font-extrabold">
              {t.count}
            </span>
          </button>
        ))}
      </div>

      <FiltersPanel label="alertas" hasFilters={hasFilters} onClear={clear}>
        <SelectFilter
          id="a-c"
          label="Criticidad"
          value={f.crit}
          onChange={(v) => setFilter("crit", v)}
          options={[
            { value: "all", label: "Todas" },
            { value: "CRITICAL", label: "Crítica" },
            { value: "HIGH", label: "Alta" },
            { value: "MEDIUM", label: "Media" },
            { value: "LOW", label: "Baja" },
          ]}
        />
        <TextFilter
          id="a-q"
          label="Cuenta"
          placeholder="Por ejemplo, 4821"
          inputMode="numeric"
          value={f.account}
          onChange={(v) => setFilter("account", v)}
        />
        <TextFilter
          id="a-m"
          label="Motivo"
          placeholder="Por ejemplo, dispositivo"
          value={f.reason}
          onChange={(v) => setFilter("reason", v)}
        />
        <SelectFilter
          id="a-e"
          label="Estado"
          value={f.status}
          onChange={(v) => setFilter("status", v)}
          options={[
            { value: "all", label: "Todos" },
            { value: "UNASSIGNED", label: "Sin asignar" },
            { value: "ASSIGNED", label: "Asignada" },
            { value: "IN_REVIEW", label: "En revisión" },
          ]}
        />
        <SelectFilter
          id="a-r"
          label="Responsable"
          value={f.assignee}
          onChange={(v) => setFilter("assignee", v)}
          options={[
            { value: "all", label: "Todos" },
            ...assignees.map((x) => ({ value: x, label: x })),
            { value: NO_ASSIGNEE, label: "Sin responsable" },
          ]}
        />
        <SelectFilter
          id="a-f"
          label="Fecha"
          value={f.date}
          onChange={(v) => {
            setCutoff(cutoffFor(v));
            setFilter("date", v);
          }}
          options={[
            { value: "all", label: "Todas las fechas" },
            { value: "7d", label: "Últimos 7 días" },
            { value: "48h", label: "Últimas 48 horas" },
            { value: "today", label: "Hoy" },
          ]}
        />
        <SelectFilter
          id="a-a"
          label="Acción"
          value={f.action}
          onChange={(v) => setFilter("action", v)}
          options={[
            { value: "all", label: "Todas" },
            { value: "take", label: "Se puede tomar" },
            { value: "none", label: "Sin acción disponible" },
          ]}
        />
      </FiltersPanel>

      {notice && (
        <div
          key={notice.text}
          role={notice.kind === "ok" ? "status" : "alert"}
          className={`anim-notice flex items-start justify-between gap-3 border px-4 py-3 text-[15px] leading-[22px] font-semibold ${
            notice.kind === "ok"
              ? "border-[#0b6b4a] bg-[#e2f5ec] text-[#0b4f37]"
              : "border-danger bg-[#fbeaea] text-danger"
          }`}
        >
          <span>
            {notice.kind === "ok" ? "✓ " : "⚠ "}
            {notice.text}
          </span>
          <button
            type="button"
            className="-my-1 flex-none rounded-full px-2 text-lg leading-none hover:bg-black/5"
            aria-label="Cerrar aviso"
            onClick={() => setNotice(null)}
          >
            ×
          </button>
        </div>
      )}

      <div className="flex min-w-0 flex-wrap items-start gap-5">
        <div className="flex min-w-0 flex-[1_1_560px] flex-col gap-4">
          <div className="tblwrap" role="table" aria-label="Alertas">
            <div className="gh g-alerts" role="row">
              <div role="columnheader">Criticidad</div>
              <div role="columnheader">Cuenta</div>
              <div role="columnheader">Motivo</div>
              <div role="columnheader">Estado</div>
              <div role="columnheader">Responsable</div>
              <div role="columnheader">Fecha</div>
              <div role="columnheader">Acción</div>
            </div>

            {alerts.loading && (
              <Loading
                label="alertas"
                className="md:min-w-[1030px]"
                grid="g-alerts"
                widths={["90px", "70px", "75%", "90px", "100px", "70px", "70px"]}
              />
            )}
            {alerts.error && (
              <LoadError
                title="No pudimos cargar las alertas"
                text={errorMessage(alerts.error, "Hubo un problema con el servidor. Intenta de nuevo en un momento.")}
                onRetry={alerts.reload}
              />
            )}
            {alerts.data && list.length === 0 && (
              <Empty
                title="Tu bandeja está al día"
                text="No hay alertas por atender en este momento. Cuando el sistema genere una nueva, aparecerá aquí."
              />
            )}
            {list.length > 0 && filtered.length === 0 && (
              <Empty title={emptyTitle} text={emptyText} onClear={hasFilters ? clear : undefined} />
            )}
            {pagination.visibleItems.map((a, i) => (
              <AlertRow
                key={a.id}
                index={i}
                alert={a}
                canTake={canTake}
                taking={taking === a.id}
                onOpen={() => setSelId(a.id)}
                onTake={() => take(a)}
              />
            ))}
          </div>

          {filtered.length > 0 && (
            <Pagination
              label="alertas"
              total={pagination.total}
              page={pagination.page}
              totalPages={pagination.totalPages}
              size={size}
              onPage={setPage}
              onSize={setSize}
            />
          )}
        </div>

        {selected && (
          <Preview
            alert={selected}
            canTake={canTake}
            taking={taking === selected.id}
            onClose={closePreview}
            onTake={() => take(selected)}
          />
        )}
      </div>
    </div>
  );
}

function badges(alert: Alert) {
  const crit = CRITICALITY[alert.criticality];
  const state = ALERT_STATUS[alert.status];
  return {
    crit: <span className={`badge ${crit.cls}`}>{crit.label}</span>,
    state: <span className={`badge ${state.cls}`}>{state.label}</span>,
  };
}

function TakeButton({
  alert: a,
  taking,
  onTake,
  className = "",
}: {
  alert: Alert;
  taking: boolean;
  onTake: () => void;
  className?: string;
}) {
  return (
    <button
      type="button"
      className={`btn ${className}`}
      onClick={onTake}
      disabled={taking}
      aria-label={`Tomar la alerta de la cuenta ${a.account}`}
    >
      {taking ? "Tomando…" : "Tomarla"}
    </button>
  );
}

function AlertRow({
  alert: a,
  index,
  canTake,
  taking,
  onOpen,
  onTake,
}: {
  alert: Alert;
  index: number;
  canTake: boolean;
  taking: boolean;
  onOpen: () => void;
  onTake: () => void;
}) {
  const b = badges(a);
  return (
    <div className="gr g-alerts anim-row" style={{ "--i": index } as CSSProperties} role="row">
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
        {b.state}
      </div>
      <div role="cell" data-label="Responsable">
        <span className="clip2" title={a.assigneeName || undefined}>
          {a.assigneeName || "—"}
        </span>
      </div>
      <div role="cell" data-label="Fecha" className="text-ink-soft">
        {formatShortDate(a.raisedAt)}
      </div>
      <div role="cell" className="empty:hidden">
        {canTake && a.status === "UNASSIGNED" && (
          <TakeButton alert={a} taking={taking} onTake={onTake} className="btn-take" />
        )}
      </div>
    </div>
  );
}

function Preview({
  alert: a,
  canTake,
  taking,
  onClose,
  onTake,
}: {
  alert: Alert;
  canTake: boolean;
  taking: boolean;
  onClose: () => void;
  onTake: () => void;
}) {
  const b = badges(a);
  const close = useRef<HTMLButtonElement>(null);

  // When it opens, focus moves to the panel; Escape closes it.
  useEffect(() => {
    close.current?.focus();
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [a.id, onClose]);

  return (
    <>
      {/* Below xl the preview is an overlay panel; at xl it sits next to the table. */}
      <div className="anim-fade fixed inset-0 z-40 bg-black/40 xl:hidden" aria-hidden="true" onClick={onClose} />
      <aside
        className="card anim-panel fixed inset-x-0 bottom-0 z-50 flex max-h-[80vh] flex-col gap-3.5 overflow-y-auto shadow-[0_-8px_24px_rgba(0,0,0,0.18)] sm:inset-y-0 sm:right-0 sm:left-auto sm:max-h-none sm:w-[360px] xl:sticky xl:top-24 xl:z-auto xl:max-h-[calc(100vh-7rem)] xl:w-auto xl:min-w-[260px] xl:flex-[0_1_320px] xl:shadow-none"
        aria-label="Vista previa de la alerta"
      >
        <div className="flex items-center justify-between gap-2">
          <h2 className="m-0 font-display text-lg font-extrabold">Vista previa</h2>
          <button ref={close} type="button" className="btn2 px-4" onClick={onClose} aria-label="Cerrar vista previa">
            Cerrar
          </button>
        </div>
        <div className="flex flex-wrap gap-2">
          {b.crit}
          {b.state}
        </div>
        <div className="text-base leading-6 font-semibold">{a.reason}</div>
        <KeyValue
          className="grid-cols-[100px_1fr]"
          items={[
            { k: "Cuenta", v: a.account },
            { k: "Responsable", v: a.assigneeName || "—" },
            { k: "Fecha", v: formatShortDate(a.raisedAt) },
          ]}
        />
        <div className="border border-dashed border-line-strong bg-soft px-4 py-3.5 text-sm leading-5 text-ink-soft">
          <strong className="text-ink">Detalle: próximamente.</strong> En esta versión solo puedes ver el resumen y tomar
          alertas sin dueño.
        </div>
        {canTake && a.status === "UNASSIGNED" && <TakeButton alert={a} taking={taking} onTake={onTake} />}
      </aside>
    </>
  );
}
