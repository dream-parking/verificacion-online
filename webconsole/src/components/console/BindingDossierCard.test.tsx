import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { RequestDetail } from "@/lib/console/api";
import { BindingDossierCard } from "./BindingDossierCard";

const dossier = (extra: Partial<NonNullable<RequestDetail["bindingDossier"]>> = {}): RequestDetail["bindingDossier"] => ({
  id: 1,
  requestId: "r1",
  frozenAt: "2026-10-28T15:00:00Z",
  retainedUntil: "2036-10-28T15:00:00Z",
  signature: { contractVersion: "2026.1", contractFingerprint: "ab12cd34", signedAt: "2026-10-28T15:00:00Z" },
  evidence: {
    schemaVersion: 1,
    identity: {
      dui: "01234567-8",
      firstNames: "Ana",
      lastNames: "Pérez",
      birthDate: "1990-01-01",
      expiryDate: "2030-01-01",
      customerNumber: "CLI-0000001",
      confirmedAt: "2026-10-28T14:00:00Z",
    },
    consent: { noticeVersion: "v3", acceptedAt: "2026-10-28T13:00:00Z", signalsAccepted: true, termsAccepted: true },
    rnpn: { outcome: "MATCH", simulated: true, attempts: 1, provider: "sim", checkedAt: "2026-10-28T14:30:00Z", mismatchedFields: [] },
    liveness: { status: "CHECKED", confidence: 0.93, attempts: 1, provider: "p", checkedAt: "2026-10-28T14:10:00Z" },
    risk: { lowMax: 39, mediumMax: 69, reviewThreshold: 70, assessment: { level: "LOW", score: 22 } },
    decisions: [{}, {}],
  },
  ...extra,
} as NonNullable<RequestDetail["bindingDossier"]>);

describe("BindingDossierCard", () => {
  it("sin expediente explica que se arma al firmar", () => {
    render(<BindingDossierCard dossier={null} />);
    expect(screen.getByText("Sin contrato firmado")).toBeInTheDocument();
    expect(screen.getByText(/se arma cuando la persona firma/)).toBeInTheDocument();
  });

  it("muestra el contrato firmado: versión, huella y fecha", () => {
    render(<BindingDossierCard dossier={dossier()} />);
    expect(screen.getByText("2026.1")).toBeInTheDocument();
    expect(screen.getByText("ab12cd34")).toBeInTheDocument();
    expect(screen.getByText("Firmado el")).toBeInTheDocument();
  });

  it("dice cuándo se congeló y hasta cuándo se retiene", () => {
    render(<BindingDossierCard dossier={dossier()} />);
    expect(screen.getByText("Congelado el")).toBeInTheDocument();
    expect(screen.getByText("Se retiene hasta")).toBeInTheDocument();
    expect(screen.getByText(/no se edita y no se borra antes de la fecha de retención/)).toBeInTheDocument();
  });

  it("resume la evidencia congelada", () => {
    render(<BindingDossierCard dossier={dossier()} />);
    expect(screen.getByText("Ana Pérez · 01234567-8")).toBeInTheDocument();
    expect(screen.getByText("CLI-0000001")).toBeInTheDocument();
    expect(screen.getByText(/Aviso v3/)).toBeInTheDocument();
    expect(screen.getByText(/Coincide · simulado/)).toBeInTheDocument();
    expect(screen.getByText(/Confianza 93 %/)).toBeInTheDocument();
    expect(screen.getByText("Umbrales con los que se calculó: Bajo hasta 39, Medio hasta 69, revisión reforzada sobre 70.")).toBeInTheDocument();
    expect(screen.getByText("2 registradas")).toBeInTheDocument();
  });

  it("no se rompe si al expediente le faltan partes", () => {
    render(<BindingDossierCard dossier={dossier({ signature: null, evidence: null, retainedUntil: null })} />);
    expect(screen.getAllByText("—").length).toBeGreaterThanOrEqual(4);
    expect(screen.queryByText("Evidencia que sustentó la aprobación")).not.toBeInTheDocument();
  });

  it("nombra los datos que no coinciden en el cotejo y la prueba de vida fallida", () => {
    const base = dossier()!;
    render(
      <BindingDossierCard
        dossier={{
          ...base,
          evidence: {
            ...base.evidence!,
            rnpn: { ...base.evidence!.rnpn!, outcome: "NO_MATCH", simulated: false, mismatchedFields: ["DUI", "BIRTH_DATE"] },
            liveness: { ...base.evidence!.liveness!, status: "FAILED" },
          },
        }}
      />,
    );
    expect(screen.getByText(/No coincide \(Número de DUI, Fecha de nacimiento\)/)).toBeInTheDocument();
    expect(screen.getByText(/Falló/)).toBeInTheDocument();
  });
});
