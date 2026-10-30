"use client";

import { useEffect, useId, useRef, useState, type FormEvent } from "react";
import { useConsole } from "@/components/console/ConsoleProvider";
import { LoadError } from "@/components/console/Ui";
import { ApiError } from "@/lib/console/api";
import { formatDateTime } from "@/lib/console/format";
import { errorMessage, useLoad } from "@/lib/console/hooks";
import {
  OUTCOME,
  OUTCOME_KINDS,
  REASON_MAX,
  fetchOutcomes,
  listedText,
  outcomeError,
  registerOutcome,
  validateReason,
  type Outcome,
  type OutcomeKind,
} from "@/lib/console/outcomes";
import { signOut } from "@/lib/console/session";

/** What registering this outcome will do to the internal risk lists, given the one in force. */
function effectOf(next: OutcomeKind, current: Outcome | undefined): string {
  if (next === "FRAUDE_CONFIRMADO") {
    return "El DUI, el teléfono y el dispositivo de esta solicitud pasan a la lista interna de fraude confirmado.";
  }
  if (current && current.listed.length > 0) {
    return `Se sacan de las listas internas ${listedText(current.listed)} que el desenlace anterior había puesto.`;
  }
  return "Solo se registra: no agrega ni quita nada de las listas internas.";
}

function ReasonDialog({
  next,
  current,
  saving,
  error,
  onCancel,
  onConfirm,
}: {
  next: OutcomeKind;
  current: Outcome | undefined;
  saving: boolean;
  error: string | null;
  onCancel: () => void;
  onConfirm: (reason: string) => void;
}) {
  const id = useId();
  const [reason, setReason] = useState("");
  const [attempted, setAttempted] = useState(false);
  const field = useRef<HTMLTextAreaElement>(null);
  const checked = validateReason(reason);
  const shown = error ?? (attempted ? checked.error : undefined);
  const isCorrection = !!current;

  // Focus starts on the reason; Escape closes it (except while saving).
  useEffect(() => {
    field.current?.focus();
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && !saving && onCancel();
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [saving, onCancel]);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setAttempted(true);
    if (checked.value !== undefined) onConfirm(checked.value);
  };

  return (
    <>
      <div className="anim-fade fixed inset-0 z-40 bg-black/40" aria-hidden="true" onClick={() => !saving && onCancel()} />
      <form
        role="dialog"
        aria-modal="true"
        aria-labelledby={`${id}-t`}
        aria-describedby={`${id}-d`}
        onSubmit={submit}
        noValidate
        className="card anim-notice fixed inset-x-4 top-1/2 z-50 mx-auto flex max-h-[calc(100vh-2rem)] max-w-[520px] -translate-y-1/2 flex-col gap-4 overflow-y-auto shadow-[0_12px_40px_rgba(0,0,0,0.25)]"
      >
        <h2 id={`${id}-t`} className="m-0 font-display text-xl font-extrabold">
          {isCorrection ? OUTCOME[next].correction : OUTCOME[next].action}
        </h2>
        <p id={`${id}-d`} className="m-0 text-[15px] leading-[22px]">
          {effectOf(next, current)} El desenlace anterior no se borra: queda en el historial.
        </p>
        <div>
          <label htmlFor={`${id}-r`} className="lbl">
            Motivo
          </label>
          <textarea
            ref={field}
            id={`${id}-r`}
            className="fld min-h-[84px]"
            maxLength={REASON_MAX + 20}
            value={reason}
            disabled={saving}
            aria-invalid={shown ? true : undefined}
            aria-describedby={`${id}-c`}
            onChange={(e) => setReason(e.target.value)}
          />
          <p id={`${id}-c`} className="mt-1.5 mb-0 text-[13px] leading-[18px] text-muted">
            {reason.trim().length} de {REASON_MAX} caracteres. Queda en la bitácora del caso.
          </p>
          {shown && (
            <p role="alert" className="mt-1.5 mb-0 text-[13px] leading-[18px] font-semibold text-danger">
              {shown}
            </p>
          )}
        </div>
        <div className="flex flex-wrap justify-end gap-3">
          <button type="button" className="btn2" onClick={onCancel} disabled={saving}>
            Cancelar
          </button>
          <button type="submit" className="btn" disabled={saving}>
            {saving ? "Guardando…" : isCorrection ? "Corregir" : "Confirmar"}
          </button>
        </div>
      </form>
    </>
  );
}

function Entry({ outcome }: { outcome: Outcome }) {
  const kind = OUTCOME[outcome.outcome];
  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex flex-wrap items-center gap-2.5">
        <span className={`badge ${kind?.cls ?? "b-none"} px-3.5 py-[5px] text-[15px]`}>{kind?.label ?? outcome.outcome}</span>
        <span className="text-sm text-muted">
          {outcome.registeredBy}, el {formatDateTime(outcome.registeredAt)}
        </span>
      </div>
      <p className="m-0 text-base leading-6">{outcome.reason}</p>
      {outcome.listed.length > 0 && (
        <p className="m-0 text-sm leading-5">Puso en la lista de fraude confirmado: {listedText(outcome.listed)}.</p>
      )}
      {outcome.revoked.length > 0 && (
        <p className="m-0 text-sm leading-5">Sacó de las listas lo que el anterior había puesto: {listedText(outcome.revoked)}.</p>
      )}
    </div>
  );
}

/**
 * Outcome of the case (VDI-145): confirmed fraud or false positive, who registered it and why, with the option to
 * correct it. Only fraud analysts and administrators can register; everyone else reads it.
 */
export function CaseOutcomeCard({ requestId }: { requestId: string }) {
  const { user, token } = useConsole();
  const canRegister = user.role === "FRAUD_ANALYST" || user.role === "ADMIN";
  const outcomes = useLoad(`desenlace-${requestId}`, (t) => fetchOutcomes(t, requestId));
  const [pending, setPending] = useState<OutcomeKind | null>(null);
  const [saving, setSaving] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const list = outcomes.data ?? [];
  const current = list[list.length - 1];
  const previous = list.slice(0, -1).reverse();
  // The action offered to correct is the opposite of the one in force.
  const options = current ? OUTCOME_KINDS.filter((k) => k !== current.outcome) : OUTCOME_KINDS;

  const close = () => {
    setPending(null);
    setFailure(null);
  };

  const confirm = async (reason: string) => {
    if (!pending) return;
    setSaving(true);
    setFailure(null);
    try {
      const saved = await registerOutcome(token, requestId, pending, reason);
      setPending(null);
      setNotice(`Desenlace registrado: ${OUTCOME[saved.outcome]?.label.replace(/^\S+\s/, "") ?? saved.outcome}.`);
      outcomes.reload();
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) signOut();
      setFailure(outcomeError(e));
    } finally {
      setSaving(false);
    }
  };

  if (outcomes.loading) {
    return (
      <div className="flex flex-col gap-3" role="status" aria-label="Cargando desenlace">
        <div className="skel w-40" />
        <div className="skel w-2/3" />
      </div>
    );
  }
  if (outcomes.error) {
    return (
      <LoadError
        title="No pudimos cargar el desenlace"
        text={errorMessage(outcomes.error, "Hubo un problema con el servidor. Intenta de nuevo en un momento.")}
        onRetry={outcomes.reload}
      />
    );
  }

  return (
    <div className="flex flex-col gap-4">
      {notice && (
        <div
          role="status"
          className="anim-notice flex items-start justify-between gap-3 border border-[#0b6b4a] bg-[#e2f5ec] px-4 py-3 text-[15px] leading-[22px] font-semibold text-[#0b4f37]"
        >
          <span>✓ {notice}</span>
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

      {current ? (
        <Entry outcome={current} />
      ) : (
        <div>
          <p className="m-0 text-[15px] font-semibold">Sin desenlace registrado</p>
          <p className="mt-1 mb-0 text-sm leading-5 text-muted">
            Cuando se sepa si el caso fue un fraude o un falso positivo, regístralo aquí. Un fraude confirmado alimenta
            las listas internas de riesgo.
          </p>
        </div>
      )}

      {canRegister ? (
        <div className="flex flex-wrap gap-3">
          {options.map((kind) => (
            <button key={kind} type="button" className={current ? "btn2" : kind === "FRAUDE_CONFIRMADO" ? "btn" : "btn2"} onClick={() => setPending(kind)}>
              {current ? OUTCOME[kind].correction : OUTCOME[kind].action}
            </button>
          ))}
        </div>
      ) : (
        <p className="m-0 text-sm leading-5 text-muted">
          Solo los analistas de fraude y los administradores pueden registrar o corregir el desenlace.
        </p>
      )}

      {previous.length > 0 && (
        <details>
          <summary className="cursor-pointer text-sm font-semibold">Historial de desenlaces ({previous.length})</summary>
          <div className="mt-3 flex flex-col gap-4">
            {previous.map((o) => (
              <Entry key={o.id} outcome={o} />
            ))}
          </div>
        </details>
      )}

      {pending && (
        <ReasonDialog next={pending} current={current} saving={saving} error={failure} onCancel={close} onConfirm={confirm} />
      )}
    </div>
  );
}
