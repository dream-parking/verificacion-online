"use client";

import { useId, useState, type FormEvent } from "react";
import { ApiError } from "@/lib/console/api";
import { signOut } from "@/lib/console/session";
import {
  changeError,
  changeProfileTolerance,
  validateTolerance,
  type ProfileTolerance,
} from "@/lib/console/profileTolerance";

/**
 * Form for an administrator to change how far the real activity can go over the declared profile before
 * it counts as a deviation (VDI-138). It applies from the next movement; deviations already detected keep
 * the tolerance they were detected with.
 */
export function ProfileToleranceForm({
  tolerance,
  token,
  onChanged,
}: {
  tolerance: ProfileTolerance;
  token: string;
  onChanged: (next: ProfileTolerance) => void;
}) {
  const id = useId();
  const [value, setValue] = useState(String(tolerance.tolerancePercent));
  const [attempted, setAttempted] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const result = validateTolerance(value, tolerance);
  const error = serverError ?? (attempted ? result.error : undefined);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setAttempted(true);
    setServerError(null);
    if (result.value === undefined) return;
    setSaving(true);
    try {
      onChanged(await changeProfileTolerance(token, result.value));
      setAttempted(false);
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) signOut();
      setServerError(changeError(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="card" aria-labelledby={`${id}-h`}>
      <h2 id={`${id}-h`} className="mt-0 mb-1 font-display text-lg font-extrabold">
        Cambiar la tolerancia
      </h2>
      <p className="mt-0 mb-5 text-sm leading-5 text-muted">
        Margen sobre lo declarado antes de considerar un movimiento una desviación. Se aplica desde el siguiente
        movimiento que reporte el core, sin desplegar. Las desviaciones ya registradas conservan la tolerancia con la
        que se detectaron.
      </p>

      <form onSubmit={submit} noValidate className="flex flex-col gap-5">
        <div className="max-w-[260px]">
          <label htmlFor={`${id}-v`} className="lbl">
            Tolerancia (%)
          </label>
          <input
            id={`${id}-v`}
            className="fld"
            type="text"
            inputMode="numeric"
            autoComplete="off"
            value={value}
            disabled={saving}
            aria-describedby={`${id}-v-h`}
            aria-invalid={error ? true : undefined}
            onChange={(e) => {
              setValue(e.target.value);
              setServerError(null);
            }}
          />
          <p id={`${id}-v-h`} className="mt-1.5 mb-0 text-[13px] leading-[18px] text-muted">
            Entero de {tolerance.minPercent} a {tolerance.maxPercent}. Hoy es {tolerance.tolerancePercent}.
          </p>
          {error && (
            <p role="alert" className="mt-1.5 mb-0 text-[13px] leading-[18px] font-semibold text-danger">
              {error}
            </p>
          )}
        </div>
        <div>
          <button type="submit" className="btn" disabled={saving}>
            {saving ? "Guardando…" : "Guardar tolerancia"}
          </button>
        </div>
      </form>
    </section>
  );
}
