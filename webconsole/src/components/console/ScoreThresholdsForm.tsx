"use client";

import { useEffect, useId, useRef, useState, type FormEvent } from "react";
import { ApiError, type ScoreModel, type ScoreModelVersion } from "@/lib/console/api";
import { signOut } from "@/lib/console/session";
import { RISK } from "@/lib/console/format";
import {
  publishError,
  publishScoreModelVersion,
  rangesOf,
  thresholdsOf,
  validateThresholds,
  type ThresholdErrors,
  type Thresholds,
} from "@/lib/console/scoreModel";

const LEVELS = [
  { key: "low", level: "LOW" },
  { key: "medium", level: "MEDIUM" },
  { key: "high", level: "HIGH" },
] as const;

function fieldError(text: string | undefined) {
  if (!text) return null;
  return (
    <p role="alert" className="mt-1.5 mb-0 text-[13px] leading-[18px] font-semibold text-danger">
      {text}
    </p>
  );
}

/** Asks before publishing: the change applies to the next assessments only. */
function ConfirmDialog({
  current,
  next,
  version,
  saving,
  onCancel,
  onConfirm,
}: {
  current: Thresholds;
  next: Thresholds;
  version: number;
  saving: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const id = useId();
  const cancel = useRef<HTMLButtonElement>(null);
  const before = rangesOf(current);
  const after = rangesOf(next);

  // Focus starts on Cancelar, so Enter does not publish by accident; Escape closes it (except while saving).
  useEffect(() => {
    cancel.current?.focus();
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && !saving && onCancel();
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [saving, onCancel]);

  return (
    <>
      <div className="anim-fade fixed inset-0 z-40 bg-black/40" aria-hidden="true" onClick={() => !saving && onCancel()} />
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={`${id}-t`}
        aria-describedby={`${id}-d`}
        className="card anim-notice fixed inset-x-4 top-1/2 z-50 mx-auto flex max-h-[calc(100vh-2rem)] max-w-[520px] -translate-y-1/2 flex-col gap-4 overflow-y-auto shadow-[0_12px_40px_rgba(0,0,0,0.25)]"
      >
        <h2 id={`${id}-t`} className="m-0 font-display text-xl font-extrabold">
          ¿Publicar la versión {version}?
        </h2>
        <p id={`${id}-d`} className="m-0 text-[15px] leading-[22px]">
          Los umbrales nuevos se aplican a las solicitudes que se evalúen <strong>a partir de ahora</strong>. Las que ya
          se evaluaron conservan su resultado y la versión del modelo con la que se calcularon.
        </p>

        <table className="w-full border-collapse text-[14px] leading-5">
          <thead>
            <tr className="text-left text-muted">
              <th scope="col" className="py-1.5 pr-3 font-semibold">
                Nivel
              </th>
              <th scope="col" className="py-1.5 pr-3 font-semibold">
                Hoy
              </th>
              <th scope="col" className="py-1.5 font-semibold">
                Con el cambio
              </th>
            </tr>
          </thead>
          <tbody>
            {LEVELS.map(({ key, level }) => {
              const changed = before[key].from !== after[key].from || before[key].to !== after[key].to;
              return (
                <tr key={key} className="border-t border-line">
                  <th scope="row" className="py-2 pr-3 text-left font-normal">
                    <span className={`badge ${RISK[level].cls}`}>{RISK[level].label}</span>
                  </th>
                  <td className="py-2 pr-3">
                    {before[key].from} a {before[key].to}
                  </td>
                  <td className={`py-2 ${changed ? "font-bold" : ""}`}>
                    {after[key].from} a {after[key].to}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>

        <div className="flex flex-wrap justify-end gap-3">
          <button ref={cancel} type="button" className="btn2" onClick={onCancel} disabled={saving}>
            Cancelar
          </button>
          <button type="button" className="btn" onClick={onConfirm} disabled={saving}>
            {saving ? "Publicando…" : "Publicar umbrales"}
          </button>
        </div>
      </div>
    </>
  );
}

/**
 * Form for an administrator to change where the LOW and MEDIUM levels end (VDI-121). Publishing
 * creates a new version of the model; the one in force is never edited.
 */
export function ScoreThresholdsForm({
  model,
  token,
  onPublished,
}: {
  model: ScoreModel;
  token: string;
  onPublished: (version: ScoreModelVersion) => void;
}) {
  const id = useId();
  const current = thresholdsOf(model);
  const [lowMax, setLowMax] = useState(String(current.lowMax));
  const [mediumMax, setMediumMax] = useState(String(current.mediumMax));
  const [attempted, setAttempted] = useState(false);
  const [serverErrors, setServerErrors] = useState<ThresholdErrors>({});
  const [pending, setPending] = useState<Thresholds | null>(null);
  const [saving, setSaving] = useState(false);

  const result = validateThresholds(lowMax, mediumMax, current);
  // The API is only called with valid values, so its errors never overlap with these.
  const errors: ThresholdErrors = { ...(attempted ? result.errors : undefined), ...serverErrors };
  const preview = result.values ? rangesOf(result.values) : null;

  const edit = (setter: (v: string) => void, v: string) => {
    setter(v);
    setServerErrors({});
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setAttempted(true);
    setServerErrors({});
    if (result.values) setPending(result.values);
  };

  const publish = async () => {
    if (!pending) return;
    setSaving(true);
    try {
      const version = await publishScoreModelVersion(token, pending);
      setPending(null);
      setAttempted(false);
      onPublished(version);
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) signOut();
      setServerErrors(publishError(err));
      setPending(null);
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="card" aria-labelledby={`${id}-h`}>
      <h2 id={`${id}-h`} className="mt-0 mb-1 font-display text-lg font-extrabold">
        Cambiar umbrales
      </h2>
      <p className="mt-0 mb-5 text-sm leading-5 text-muted">
        Define dónde termina cada nivel. Se publica como una versión nueva del modelo, con los mismos puntos por señal; la
        versión vigente no se edita.
      </p>

      <form onSubmit={submit} noValidate className="flex flex-col gap-5">
        <div className="grid grid-cols-1 gap-x-6 gap-y-4 sm:grid-cols-2">
          <div className="min-w-0">
            <label htmlFor={`${id}-low`} className="lbl">
              Fin del nivel Bajo
            </label>
            <input
              id={`${id}-low`}
              className="fld"
              type="text"
              inputMode="numeric"
              autoComplete="off"
              value={lowMax}
              disabled={saving}
              aria-describedby={`${id}-low-h`}
              aria-invalid={errors.lowMax ? true : undefined}
              onChange={(e) => edit(setLowMax, e.target.value)}
            />
            <p id={`${id}-low-h`} className="mt-1.5 mb-0 text-[13px] leading-[18px] text-muted">
              Entero desde 0. Hoy es {current.lowMax}.
            </p>
            {fieldError(errors.lowMax)}
          </div>
          <div className="min-w-0">
            <label htmlFor={`${id}-med`} className="lbl">
              Fin del nivel Medio
            </label>
            <input
              id={`${id}-med`}
              className="fld"
              type="text"
              inputMode="numeric"
              autoComplete="off"
              value={mediumMax}
              disabled={saving}
              aria-describedby={`${id}-med-h`}
              aria-invalid={errors.mediumMax ? true : undefined}
              onChange={(e) => edit(setMediumMax, e.target.value)}
            />
            <p id={`${id}-med-h`} className="mt-1.5 mb-0 text-[13px] leading-[18px] text-muted">
              Entero menor que 100. Hoy es {current.mediumMax}. Arriba de este valor el nivel es Alto y la solicitud
              pasa a revisión reforzada.
            </p>
            {fieldError(errors.mediumMax)}
          </div>
        </div>

        {preview && (
          <p className="m-0 text-[14px] leading-5" aria-live="polite">
            Quedaría: <strong>Bajo</strong> de {preview.low.from} a {preview.low.to}, <strong>Medio</strong> de{" "}
            {preview.medium.from} a {preview.medium.to} y <strong>Alto</strong> de {preview.high.from} a{" "}
            {preview.high.to}.
          </p>
        )}

        {errors.form && (
          <div
            role="alert"
            className="anim-notice border border-danger bg-[#fbeaea] px-4 py-3 text-[14px] leading-5 font-semibold text-danger"
          >
            ⚠ {errors.form}
          </div>
        )}

        <div>
          <button type="submit" className="btn" disabled={saving}>
            Publicar umbrales
          </button>
        </div>
      </form>

      {pending && (
        <ConfirmDialog
          current={current}
          next={pending}
          version={model.version + 1}
          saving={saving}
          onCancel={() => setPending(null)}
          onConfirm={publish}
        />
      )}
    </section>
  );
}
