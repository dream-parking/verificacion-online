"use client";

import { useEffect, useState } from "react";

type CopyState = "idle" | "copied" | "failed";

/**
 * The customer number of a request (VDI-123), with a button to copy it. Until the request is submitted the customer
 * has no number.
 */
export function CustomerNumber({ value }: { value: string | null | undefined }) {
  const [state, setState] = useState<CopyState>("idle");

  // The confirmation goes away after a moment.
  useEffect(() => {
    if (state === "idle") return;
    const timer = setTimeout(() => setState("idle"), 2000);
    return () => clearTimeout(timer);
  }, [state]);

  if (!value) return <span className="text-muted">Sin número de cliente</span>;

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      setState("copied");
    } catch {
      setState("failed");
    }
  };

  return (
    <span className="inline-flex flex-wrap items-center gap-2">
      <strong className="tabular-nums">{value}</strong>
      <button
        type="button"
        className="btn2 min-h-8 px-3 text-[12px]"
        aria-label={`Copiar el número de cliente ${value}`}
        onClick={copy}
      >
        Copiar
      </button>
      <span role="status" className="text-[13px]">
        {state === "copied" && <span className="font-semibold text-[#0b6b4a]">✓ Copiado</span>}
        {state === "failed" && <span className="font-semibold text-danger">No se pudo copiar</span>}
      </span>
    </span>
  );
}
