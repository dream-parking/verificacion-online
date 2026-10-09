"use client";

import { useEffect, useId, useRef, useState, type FormEvent } from "react";
import { ApiError } from "@/lib/console/api";
import { signOut } from "@/lib/console/session";
import { changePassword, changePasswordMessage, PASSWORD_POLICY, passwordError } from "@/lib/console/users";

type Errors = { current?: string; next?: string; repeat?: string };

/** Dialog for the signed-in person to change their own password (POST /auth/change-password). */
export function ChangePasswordDialog({ email, token, onClose }: { email: string; token: string; onClose: () => void }) {
  const id = useId();
  const first = useRef<HTMLInputElement>(null);
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [repeat, setRepeat] = useState("");
  const [visible, setVisible] = useState(false);
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);
  const [serverError, setServerError] = useState("");
  const [done, setDone] = useState(false);

  // When it opens, focus goes to the first field; Escape closes it (except while saving).
  useEffect(() => {
    first.current?.focus();
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && !saving && onClose();
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [saving, onClose, done]);

  const validate = (): Errors => {
    const found: Errors = {};
    if (!current) found.current = "Escribe tu contraseña actual.";
    const policy = passwordError(next, email);
    if (policy) found.next = policy;
    else if (next === current) found.next = "La contraseña nueva debe ser distinta de la actual.";
    else if (repeat !== next) found.repeat = "Las contraseñas no coinciden.";
    return found;
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const found = validate();
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    setSaving(true);
    setServerError("");
    try {
      await changePassword(token, current, next);
      setDone(true);
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) signOut();
      setServerError(changePasswordMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const fieldError = (text?: string) =>
    text && (
      <p role="alert" className="mt-1.5 mb-0 text-[13px] leading-[18px] font-semibold text-danger">
        {text}
      </p>
    );

  return (
    <>
      <div className="anim-fade fixed inset-0 z-40 bg-black/40" aria-hidden="true" onClick={() => !saving && onClose()} />
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={`${id}-t`}
        className="card anim-notice fixed inset-x-4 top-1/2 z-50 mx-auto flex max-h-[calc(100vh-2rem)] max-w-[480px] -translate-y-1/2 flex-col gap-4 overflow-y-auto shadow-[0_12px_40px_rgba(0,0,0,0.25)]"
      >
        <h2 id={`${id}-t`} className="m-0 font-display text-xl font-extrabold">
          Cambiar mi contraseña
        </h2>

        {done ? (
          <>
            <p role="status" className="m-0 text-[15px] leading-[22px]">
              ✓ Listo. Tu contraseña se cambió: úsala la próxima vez que inicies sesión.
            </p>
            <div className="flex justify-end">
              <button type="button" className="btn" onClick={onClose} autoFocus>
                Cerrar
              </button>
            </div>
          </>
        ) : (
          <form onSubmit={submit} noValidate className="flex flex-col gap-4">
            <div>
              <label htmlFor={`${id}-c`} className="lbl">
                Contraseña actual
              </label>
              <input
                ref={first}
                id={`${id}-c`}
                className="fld"
                type={visible ? "text" : "password"}
                autoComplete="current-password"
                spellCheck={false}
                value={current}
                disabled={saving}
                aria-invalid={errors.current ? true : undefined}
                onChange={(e) => setCurrent(e.target.value)}
              />
              {fieldError(errors.current)}
            </div>

            <div>
              <label htmlFor={`${id}-n`} className="lbl">
                Contraseña nueva
              </label>
              <div className="flex items-center gap-2">
                <input
                  id={`${id}-n`}
                  className="fld"
                  type={visible ? "text" : "password"}
                  autoComplete="new-password"
                  spellCheck={false}
                  value={next}
                  disabled={saving}
                  aria-describedby={`${id}-h`}
                  aria-invalid={errors.next ? true : undefined}
                  onChange={(e) => setNext(e.target.value)}
                />
                <button
                  type="button"
                  className="btn2 min-h-9 flex-none px-3 text-[12px]"
                  aria-pressed={visible}
                  onClick={() => setVisible((v) => !v)}
                >
                  {visible ? "Ocultar" : "Mostrar"}
                </button>
              </div>
              <p id={`${id}-h`} className="mt-1.5 mb-0 text-[13px] leading-[18px] text-muted">
                {PASSWORD_POLICY}
              </p>
              {fieldError(errors.next)}
            </div>

            <div>
              <label htmlFor={`${id}-r`} className="lbl">
                Repite la contraseña nueva
              </label>
              <input
                id={`${id}-r`}
                className="fld"
                type={visible ? "text" : "password"}
                autoComplete="new-password"
                spellCheck={false}
                value={repeat}
                disabled={saving}
                aria-invalid={errors.repeat ? true : undefined}
                onChange={(e) => setRepeat(e.target.value)}
              />
              {fieldError(errors.repeat)}
            </div>

            {serverError && (
              <div role="alert" className="anim-notice border border-danger bg-[#fbeaea] px-4 py-3 text-[14px] leading-5 font-semibold text-danger">
                ⚠ {serverError}
              </div>
            )}

            <div className="flex flex-wrap justify-end gap-3">
              <button type="button" className="btn2" onClick={onClose} disabled={saving}>
                Cancelar
              </button>
              <button type="submit" className="btn" disabled={saving}>
                {saving ? "Guardando…" : "Cambiar contraseña"}
              </button>
            </div>
          </form>
        )}
      </div>
    </>
  );
}
