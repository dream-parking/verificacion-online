import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { RnpnCheck, RnpnField } from "@/lib/console/api";
import { RnpnCheckCard } from "./RnpnCheckCard";

const check = (c: Partial<RnpnCheck>): RnpnCheck => ({
  outcome: "MATCH",
  simulated: true,
  provider: "simulated",
  attempts: 1,
  mismatchedFields: [],
  checkedAt: "2026-10-21T16:30:00Z",
  ...c,
});

describe("RnpnCheckCard", () => {
  it.each([undefined, null])("sin cotejo dice «Sin cotejo con el RNPN» (%s)", (value) => {
    render(<RnpnCheckCard check={value} />);
    expect(screen.getByText("Sin cotejo con el RNPN")).toBeInTheDocument();
  });

  it("coincide: muestra el resultado con símbolo, la fecha y los intentos", () => {
    render(<RnpnCheckCard check={check({})} />);
    expect(screen.getByText("✓ Coincide")).toHaveClass("s-ok");
    expect(screen.getByText(/coinciden con el RNPN/)).toBeInTheDocument();
    expect(screen.getByText(/21\/10\/2026/)).toBeInTheDocument();
    expect(screen.getByText("1 intento")).toBeInTheDocument();
    expect(screen.queryByText("No coinciden")).not.toBeInTheDocument();
  });

  it("no coincide: dice qué datos no coincidieron", () => {
    render(<RnpnCheckCard check={check({ outcome: "NO_MATCH", attempts: 2, mismatchedFields: ["LAST_NAMES", "BIRTH_DATE"] })} />);
    expect(screen.getByText("✕ No coincide")).toBeInTheDocument();
    expect(screen.getByText("No coinciden")).toBeInTheDocument();
    expect(screen.getByText("✕ Apellidos")).toBeInTheDocument();
    expect(screen.getByText("✕ Fecha de nacimiento")).toBeInTheDocument();
    expect(screen.getByText("2 intentos")).toBeInTheDocument();
  });

  it("no coincide sin campos: lo dice en vez de dejar la lista vacía", () => {
    render(<RnpnCheckCard check={check({ outcome: "NO_MATCH", mismatchedFields: null })} />);
    expect(screen.getByText("El RNPN no indicó qué datos.")).toBeInTheDocument();
  });

  it("no disponible: explica que la solicitud queda en espera y cuenta los reintentos", () => {
    render(<RnpnCheckCard check={check({ outcome: "UNAVAILABLE", attempts: 3, mismatchedFields: ["DUI"] })} />);
    expect(screen.getByText("○ No disponible")).toBeInTheDocument();
    expect(screen.getByText(/queda en espera/)).toBeInTheDocument();
    expect(screen.getByText("3 intentos")).toBeInTheDocument();
    // Fields only matter when the RNPN answered and something differs.
    expect(screen.queryByText("✕ Número de DUI")).not.toBeInTheDocument();
  });

  it("marca «Simulado» solo mientras el RNPN es el servicio simulado", () => {
    const { rerender } = render(<RnpnCheckCard check={check({})} />);
    expect(screen.getByText("Simulado")).toBeInTheDocument();
    expect(screen.getByText(/servicio simulado/)).toBeInTheDocument();

    rerender(<RnpnCheckCard check={check({ simulated: false })} />);
    expect(screen.queryByText("Simulado")).not.toBeInTheDocument();
    expect(screen.queryByText(/servicio simulado/)).not.toBeInTheDocument();
  });

  it("muestra el código si llega un resultado o un campo que la consola aún no conoce", () => {
    render(
      <RnpnCheckCard
        check={check({ outcome: "NO_MATCH", mismatchedFields: ["GENDER" as RnpnField] })}
      />,
    );
    expect(screen.getByText("✕ GENDER")).toBeInTheDocument();
  });

  it("un resultado desconocido se muestra con su código", () => {
    render(<RnpnCheckCard check={check({ outcome: "PARTIAL" as RnpnCheck["outcome"] })} />);
    expect(screen.getByText("PARTIAL")).toBeInTheDocument();
  });
});
