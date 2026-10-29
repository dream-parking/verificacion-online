"use client";

import Link from "next/link";
import { useId, useRef, useState, type FormEvent } from "react";
import { useConsole } from "@/components/console/ConsoleProvider";
import { BindingDossierCard } from "@/components/console/BindingDossierCard";
import { PageHeading } from "@/components/console/Ui";
import { ApiError } from "@/lib/console/api";
import { signOut } from "@/lib/console/session";
import {
  SEARCH_RULES,
  findDossiers,
  searchError,
  validateDossierNumber,
  type ArchivedDossier,
  type DossierSearch,
} from "@/lib/console/dossiers";

type Outcome = { mode: DossierSearch; value: string; dossiers: ArchivedDossier[] };

export function DossiersView() {
  const { token } = useConsole();
  const id = useId();
  const [mode, setMode] = useState<DossierSearch>("request");
  const [raw, setRaw] = useState("");
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const [searching, setSearching] = useState(false);
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  // Only the answer to the last search is shown, even if an earlier one finishes later.
  const lastSearch = useRef(0);

  const rules = SEARCH_RULES[mode];

  const choose = (next: DossierSearch) => {
    // A search still pending belongs to the other tab: its answer must not show up here.
    lastSearch.current++;
    setSearching(false);
    setMode(next);
    setRaw("");
    setFieldError(null);
    setFailure(null);
    setOutcome(null);
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const checked = validateDossierNumber(mode, raw);
    setFailure(null);
    if (checked.error !== undefined) {
      setFieldError(checked.error);
      return;
    }
    setFieldError(null);
    setSearching(true);
    const mine = ++lastSearch.current;
    try {
      const dossiers = await findDossiers(token, mode, checked.value);
      if (mine === lastSearch.current) setOutcome({ mode, value: checked.value, dossiers });
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) signOut();
      if (mine === lastSearch.current) {
        setOutcome(null);
        setFailure(searchError(err, mode));
      }
    } finally {
      if (mine === lastSearch.current) setSearching(false);
    }
  };

  return (
    <div className="anim-stagger flex max-w-[860px] min-w-0 flex-col gap-5">
      <PageHeading title="Expedientes de vinculación">
        Consulta la copia inmutable de la evidencia que se congeló al firmar, por número de solicitud o de cliente.
      </PageHeading>

      <section className="card" aria-labelledby={`${id}-h`}>
        <h2 id={`${id}-h`} className="mt-0 mb-1 font-display text-lg font-extrabold">
          Buscar un expediente
        </h2>
        <p className="mt-0 mb-5 text-sm leading-5 text-muted">
          Trae datos personales: cada consulta queda en la auditoría de accesos, con tu usuario, la IP y la fecha y hora.
        </p>

        <div className="mb-4 flex flex-wrap gap-2.5" role="group" aria-label="Buscar por">
          {(Object.keys(SEARCH_RULES) as DossierSearch[]).map((m) => (
            <button key={m} type="button" className="tab" aria-pressed={mode === m} onClick={() => choose(m)}>
              {SEARCH_RULES[m].label}
            </button>
          ))}
        </div>

        <form onSubmit={submit} noValidate className="flex flex-col gap-5">
          <div className="max-w-[320px]">
            <label htmlFor={`${id}-n`} className="lbl">
              {rules.label}
            </label>
            <input
              id={`${id}-n`}
              className="fld"
              type="text"
              autoComplete="off"
              placeholder={rules.example}
              value={raw}
              disabled={searching}
              aria-invalid={fieldError ? true : undefined}
              aria-describedby={fieldError ? `${id}-e` : undefined}
              onChange={(e) => {
                setRaw(e.target.value);
                setFieldError(null);
              }}
            />
            {fieldError && (
              <p id={`${id}-e`} role="alert" className="mt-1.5 mb-0 text-[13px] leading-[18px] font-semibold text-danger">
                {fieldError}
              </p>
            )}
          </div>
          <div>
            <button type="submit" className="btn" disabled={searching}>
              {searching ? "Buscando…" : "Buscar expediente"}
            </button>
          </div>
        </form>
      </section>

      {failure && (
        <div role="alert" className="anim-notice border border-danger bg-[#fbeaea] px-4 py-3 text-[14px] leading-5 font-semibold text-danger">
          ⚠ {failure}
        </div>
      )}

      {outcome && outcome.dossiers.length === 0 && (
        <div role="status" className="card">
          <p className="m-0 text-[15px] font-semibold">Sin expedientes</p>
          <p className="mt-1 mb-0 text-sm leading-5 text-muted">
            El cliente {outcome.value} todavía no ha firmado ninguna solicitud, así que no tiene expediente.
          </p>
        </div>
      )}

      {outcome?.dossiers.map((d) => (
        <section key={d.dossier.id ?? d.requestNumber} className="card" aria-label={`Expediente de ${d.requestNumber}`}>
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <h2 className="m-0 font-display text-lg font-extrabold">
              {d.requestNumber}
              {d.customerNumber && <span className="font-normal text-muted"> · {d.customerNumber}</span>}
            </h2>
            {d.dossier.requestId && (
              <Link href={`/requests/${d.dossier.requestId}`} className="font-semibold underline">
                Ver la solicitud
              </Link>
            )}
          </div>
          <BindingDossierCard dossier={d.dossier} />
        </section>
      ))}
    </div>
  );
}
