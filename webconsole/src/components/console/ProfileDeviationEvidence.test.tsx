import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ProfileDeviationEvidence } from "./ProfileDeviationEvidence";

const evidence = (extra: Record<string, unknown> = {}) => ({
  source: "PROFILE_TRACKING",
  month: "2026-10",
  deviation: "INFLOW_ABOVE_RANGE",
  declared: 1000,
  actual: 1500,
  limit: 1200,
  tolerancePercent: 20,
  ...extra,
});

describe("ProfileDeviationEvidence", () => {
  it("dice qué aspecto del perfil se rompió, con lo declarado, lo real, el límite y la tolerancia", () => {
    render(<ProfileDeviationEvidence evidence={evidence()} />);
    expect(screen.getByText("Ingresos por encima del rango mensual declarado")).toBeInTheDocument();
    expect(screen.getByText("USD 1,000")).toBeInTheDocument();
    expect(screen.getByText("USD 1,500")).toBeInTheDocument();
    expect(screen.getByText("USD 1,200")).toBeInTheDocument();
    expect(screen.getByText("20 %")).toBeInTheDocument();
    expect(screen.getByText("2026-10")).toBeInTheDocument();
  });

  it("cuenta movimientos en lugar de dólares cuando la desviación es de cantidad", () => {
    render(
      <ProfileDeviationEvidence
        evidence={evidence({ deviation: "MORE_TRANSACTIONS_THAN_DECLARED", declared: 10, actual: 15, limit: 12 })}
      />,
    );
    expect(screen.getByText("Más movimientos de entrada que los declarados")).toBeInTheDocument();
    expect(screen.getByText("15 movimientos")).toBeInTheDocument();
  });

  it("con un tipo que no conoce usa un título general y no se rompe con valores que faltan", () => {
    render(<ProfileDeviationEvidence evidence={evidence({ deviation: "NUEVO", actual: undefined })} />);
    expect(screen.getByText("Desviación del perfil declarado")).toBeInTheDocument();
    expect(screen.getByText("—")).toBeInTheDocument();
  });

  it.each([[undefined], [null], [{}], [{ source: "MANUAL" }]])("no muestra nada para otras alertas (%#)", (value) => {
    const { container } = render(<ProfileDeviationEvidence evidence={value} />);
    expect(container).toBeEmptyDOMElement();
  });

  it.each([["HORARIO_NOCTURNO"], ["DISPOSITIVO_NUEVO"], [null], [undefined]])(
    "una alerta de otro tipo (%s) sin evidencia de perfil no muestra nada",
    (typeCode) => {
      const { container } = render(<ProfileDeviationEvidence evidence={{}} typeCode={typeCode} />);
      expect(container).toBeEmptyDOMElement();
    },
  );

  it.each([
    ["PERFIL_EXCEDIDO", "Movimientos por encima de lo declarado en el perfil"],
    ["ORIGEN_DISTINTO", "Dinero recibido de un tipo que no declaró"],
  ])("una alerta %s sin evidencia dice de qué trata y que falta el detalle", (typeCode, aspect) => {
    render(<ProfileDeviationEvidence evidence={{}} typeCode={typeCode} />);
    expect(screen.getByText("Aspecto del perfil que se rompió")).toBeInTheDocument();
    expect(screen.getByText(aspect)).toBeInTheDocument();
    expect(screen.getByText(/no trae el detalle de la desviación/)).toBeInTheDocument();
    expect(screen.queryByText("Declarado")).not.toBeInTheDocument();
  });

  it("con evidencia del seguimiento del perfil no repite el aviso de detalle faltante", () => {
    render(<ProfileDeviationEvidence evidence={evidence()} typeCode="PERFIL_EXCEDIDO" />);
    expect(screen.getByText("USD 1,500")).toBeInTheDocument();
    expect(screen.queryByText(/no trae el detalle/)).not.toBeInTheDocument();
  });
});
