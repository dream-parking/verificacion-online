import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { RiskDecision } from "@/lib/console/api";
import { RiskDecisionLog } from "./RiskDecisionLog";

const decision = (d: Partial<RiskDecision>): RiskDecision => ({
  id: 1,
  decision: "ENHANCED_REVIEW",
  reason: "Score 82 is above the enhanced-review threshold of 69 (score model v2): request held for enhanced review.",
  score: 82,
  threshold: 69,
  modelVersion: 2,
  decidedBy: "SYSTEM",
  decidedAt: "2026-10-20T18:05:00Z",
  channel: null,
  ...d,
});

describe("RiskDecisionLog", () => {
  it.each([undefined, null, []])("sin decisiones dice «Sin decisiones registradas» (%#)", (decisions) => {
    render(<RiskDecisionLog decisions={decisions} />);
    expect(screen.getByText("Sin decisiones registradas.")).toBeInTheDocument();
    expect(screen.queryByRole("list")).not.toBeInTheDocument();
  });

  it("muestra la decisión, el motivo en español, la versión del modelo, la fecha y quién decidió", () => {
    render(<RiskDecisionLog decisions={[decision({})]} />);
    const item = within(screen.getByRole("list", { name: "Bitácora de decisiones" })).getByRole("listitem");

    expect(item).toHaveTextContent("⚑ Revisión reforzada");
    expect(item).toHaveTextContent("El score 82 supera el umbral de revisión reforzada de 69");
    expect(item).toHaveTextContent("modelo de score v2");
    expect(item).toHaveTextContent(/20\/10\/2026/);
    expect(item).toHaveTextContent("decidió: Sistema (regla automática)");
    // The API reason is in English: it is not shown as is.
    expect(item).not.toHaveTextContent("enhanced-review threshold");
  });

  it("una decisión del flujo automático dice que no supera el umbral", () => {
    render(<RiskDecisionLog decisions={[decision({ decision: "AUTOMATIC_FLOW", score: 40, decidedBy: "Ana Pérez" })]} />);
    const item = screen.getByRole("listitem");
    expect(item).toHaveTextContent("✓ Flujo automático");
    expect(item).toHaveTextContent("El score 40 no supera el umbral");
    expect(item).toHaveTextContent("decidió: Ana Pérez");
  });

  it("lista las decisiones en el orden del API y no ofrece editarlas ni borrarlas", () => {
    render(
      <RiskDecisionLog
        decisions={[decision({ id: 1, decision: "AUTOMATIC_FLOW", score: 50 }), decision({ id: 2, decidedBy: null })]}
      />,
    );
    const items = screen.getAllByRole("listitem");
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveTextContent("Flujo automático");
    expect(items[1]).toHaveTextContent("Sistema (regla automática)");
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("un escalamiento muestra el motivo del API, el canal y el analista, en español", () => {
    render(
      <RiskDecisionLog
        decisions={[
          decision({
            decision: "ESCALATED",
            channel: "VIDEO_CALL",
            reason: "Escalada a videollamada por Ana Pérez: El monto no coincide con los ingresos",
            decidedBy: "Ana Pérez",
          }),
        ]}
      />,
    );
    const item = screen.getByRole("listitem");
    expect(item).toHaveTextContent("⇪ Escalada");
    expect(item).toHaveTextContent("Escalada a videollamada por Ana Pérez: El monto no coincide con los ingresos");
    expect(item).toHaveTextContent("Canal: Videollamada");
    expect(item).toHaveTextContent("decidió: Ana Pérez");
    expect(item).not.toHaveTextContent("supera el umbral");
  });

  it("muestra el código si llega una decisión que la consola aún no conoce", () => {
    render(<RiskDecisionLog decisions={[decision({ decision: "MANUAL_RELEASE" as RiskDecision["decision"] })]} />);
    expect(screen.getByRole("listitem")).toHaveTextContent("MANUAL_RELEASE");
  });
});
