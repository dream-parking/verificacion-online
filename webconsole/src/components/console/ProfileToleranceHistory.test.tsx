import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ProfileToleranceHistory } from "./ProfileToleranceHistory";

describe("ProfileToleranceHistory", () => {
  it("lista cada cambio con el valor anterior, el nuevo y quién lo hizo, y marca el valor inicial", () => {
    render(
      <ProfileToleranceHistory
        changes={[
          { changedAt: "2026-10-28T15:00:00Z", changedBy: "Ana Pérez", previousPercent: 20, tolerancePercent: 25 },
          { changedAt: "2026-10-01T15:00:00Z", changedBy: null, previousPercent: null, tolerancePercent: 20 },
        ]}
      />,
    );
    const rows = screen.getAllByRole("row").slice(1);
    expect(rows).toHaveLength(2);
    expect(within(rows[0]).getByText("20 %")).toBeInTheDocument();
    expect(within(rows[0]).getByText("25 %")).toBeInTheDocument();
    expect(within(rows[0]).getByText("Ana Pérez")).toBeInTheDocument();
    expect(within(rows[1]).getByText("Valor inicial")).toBeInTheDocument();
  });
});
