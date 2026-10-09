"use client";

import { useEffect, useId, useRef, useState, type FormEvent } from "react";
import { signOut } from "@/lib/console/session";
import { ApiError, type ConsoleUser } from "@/lib/console/api";
import { PASSWORD_POLICY, passwordError, resetPassword, resetPasswordMessage } from "@/lib/console/users";

/** Dialog to set another person's password. The password is never stored or shown afterwards. */
export function PasswordDialog({
  user,
  token,
  isSelf,
  onClose,
  onDone,
}: {
  user: ConsoleUser;
  token: string;
  /** The administrator is changing their own password. */
  isSelf: boolean;
  onClose: () => void;
  onDone: () => void;
}) {
  const id = useId();
  const input = useRef<HTMLInputElement>(null);
  const [password, setPassword] = useState("");
  const [repeat, setRepeat] = useState("");
  const [visible, setVisible] = useState(false);
  const [attempted, setAttempted] = useState(false);
  const [saving, setSaving] = useState(false);
  const [serverError, setServerError] = useState("");

  // When it opens, focus goes to the field; Escape closes it (except while saving).
  useEffect(() => {
    input.current?.focus();
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && !saving && onClose();
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [saving, onClose]);

  const policyError = passwordError(password, user.email);
  const repeatError = repeat !== password ? "Las contraseñas no coinciden." : "";
  const showPolicyError = attempted && policyError;
  const showRepeatError = attempted && !policyError && repeatError;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setAttempted(true);
    if (policyError || repeatError) return;
    setSaving(true);
    setServerError("");
    try {
      await resetPassword(token, user.id, password);
      onDone();
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) signOut();
      setServerError(resetPasswordMessage(err));
      setSaving(false);
    }
  };

  return (
    <>
      <div className="anim-fade fixed inset-0 z-40 bg-black/40" aria-hidden="true" onClick={() => !saving && onClose()} />
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={`${id}-t`}
        className="card anim-notice fixed inset-x-4 top-1/2 z-50 mx-auto flex max-h-[calc(100vh-2rem)] max-w-[480px] -translate-y-1/2 flex-col gap-4 overflow-y-auto shadow-[0_12px_40px_rgba(0,0,0,0.25)]"
      >
        <div>
          <h2 id={`${id}-t`} className="m-0 font-display text-xl font-extrabold">
            Definir contraseña
          </h2>
          <p className="mt-1 mb-0 text-[15px] leading-[22px] text-muted [overflow-wrap:anywhere]">
            Para <strong className="text-ink">{user.fullName}</strong> ({user.email}). Reemplaza la contraseña actual.
          </p>
        </div>

        {isSelf && (
          <div className="border border-line-mid bg-soft px-4 py-3 text-[14px] leading-5">
            <strong>Es tu propia cuenta.</strong> La contraseña nueva reemplaza la actual: anótala antes de guardar,
            porque la necesitarás para volver a entrar.
          </div>
        )}

        <form onSubmit={submit} noValidate className="flex flex-col gap-4">
          <div>
            <label htmlFor={`${id}-p`} className="lbl">
              Contraseña nueva
            </label>
            <div className="flex items-center gap-2">
              <input
                ref={input}
                id={`${id}-p`}
                className="fld"
                type={visible ? "text" : "password"}
                autoComplete="new-password"
                spellCheck={false}
                value={password}
                disabled={saving}
                aria-describedby={`${id}-h`}
                aria-invalid={showPolicyError ? true : undefined}
                onChange={(e) => setPassword(e.target.value)}
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
            {showPolicyError && (
              <p role="alert" className="mt-1.5 mb-0 text-[13px] leading-[18px] font-semibold text-danger">
                {policyError}
              </p>
            )}
          </div>

          <div>
            <label htmlFor={`${id}-r`} className="lbl">
              Repite la contraseña
            </label>
            <input
              id={`${id}-r`}
              className="fld"
              type={visible ? "text" : "password"}
              autoComplete="new-password"
              spellCheck={false}
              value={repeat}
              disabled={saving}
              aria-invalid={showRepeatError ? true : undefined}
              onChange={(e) => setRepeat(e.target.value)}
            />
            {showRepeatError && (
              <p role="alert" className="mt-1.5 mb-0 text-[13px] leading-[18px] font-semibold text-danger">
                {repeatError}
              </p>
            )}
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
              {saving ? "Guardando…" : "Definir contraseña"}
            </button>
          </div>
        </form>
      </div>
    </>
  );
}
