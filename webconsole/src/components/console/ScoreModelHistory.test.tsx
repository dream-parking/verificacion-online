import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { ScoreModelVersion } from "@/lib/console/api";
import { ScoreModelHistory } from "./ScoreModelHistory";

const version = (v: Partial<ScoreModelVersion>): ScoreModelVersion => ({
  version: 1,
  name: "Score",
  low: { from: 0, to: 39 },
  medium: { from: 40, to: 69 },
  high: { from: 70, to: 100 },
  validFrom: "2026-10-01T18:00:00Z",
  validTo: null,
  inForce: false,
  publishedBy: null,
  weights: [],
  ...v,
});

describe("ScoreModelHistory", () => {
  it("lista cada versión con sus umbrales, quién la publicó y cuándo, marcando la vigente", () => {
    render(
      <ScoreModelHistory
        versions={[
          version({ version: 2, low: { from: 0, to: 29 }, medium: { from: 30, to: 59 }, high: { from: 60, to: 100 }, inForce: true, publishedBy: "Ana Pérez", validFrom: "2026-10-20T18:00:00Z" }),
          version({ version: 1, validTo: "2026-10-20T18:00:00Z" }),
        ]}
      />,
    );

    const rows = within(screen.getByRole("table", { name: "Historial de versiones del modelo" })).getAllByRole("row");
    expect(rows).toHaveLength(3);

    expect(rows[1]).toHaveAttribute("aria-current", "true");
    expect(rows[1]).toHaveTextContent("v2");
    expect(rows[1]).toHaveTextContent("✓ Vigente");
    expect(rows[1]).toHaveTextContent("0 a 29");
    expect(rows[1]).toHaveTextContent("60 a 100");
    expect(rows[1]).toHaveTextContent("Ana Pérez");
    expect(rows[1]).toHaveTextContent(/Desde 20\/10\/2026/);
    expect(rows[1]).not.toHaveTextContent("Hasta");

    expect(rows[2]).not.toHaveAttribute("aria-current");
    expect(rows[2]).not.toHaveTextContent("Vigente");
    expect(rows[2]).toHaveTextContent("Sistema (migración)");
    expect(rows[2]).toHaveTextContent(/Hasta 20\/10\/2026/);
  });

  it("muestra un guion si la versión no tiene fecha de inicio", () => {
    render(<ScoreModelHistory versions={[version({ validFrom: null })]} />);
    expect(screen.getAllByRole("row")[1]).toHaveTextContent("—");
  });
});
