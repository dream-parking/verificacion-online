import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Alert, ConsoleUser } from "@/lib/console/api";
import { AlertsView } from "./AlertsView";

const user: ConsoleUser = {
  id: "u1",
  email: "a@example.com",
  fullName: "Ana Pérez",
  initials: null,
  jobTitle: null,
  role: "FRAUD_ANALYST",
  active: true,
};

const alert = (id: string, reason: string): Alert => ({
  id,
  account: `****${id}`,
  reason,
  criticality: "HIGH",
  status: "UNASSIGNED",
  assigneeId: null,
  assigneeName: null,
  raisedAt: "2026-10-28T15:00:00Z",
});

const ALERTS = [
  alert("1", "Movimientos 4 veces por encima del perfil declarado"),
  alert("2", "Ingresos recibidos de origen distinto al declarado"),
  alert("3", "Actividad inusual en horario nocturno"),
  alert("4", "Ingresos por encima del rango mensual declarado"),
];

vi.mock("@/components/console/ConsoleProvider", () => ({
  useConsole: () => ({ user, alerts: { data: ALERTS }, takeAlert: vi.fn() }),
}));

type Detail = { typeCode?: string | null; evidence?: Record<string, unknown> | null };
let details: Record<string, Detail | undefined>;
const useLoad = vi.fn();
vi.mock("@/lib/console/hooks", async (original) => ({
  ...(await original<typeof import("@/lib/console/hooks")>()),
  // The preview asks for the detail of the alert under the key "alerta-<id>".
  useLoad: (key: string | null, ...rest: unknown[]) => {
    useLoad(key, ...rest);
    return { data: key ? details[key.replace("alerta-", "")] : undefined, loading: false, reload: vi.fn(), update: vi.fn() };
  },
}));

const TRACKED = {
  source: "PROFILE_TRACKING",
  month: "2026-10",
  deviation: "INFLOW_ABOVE_RANGE",
  declared: 1000,
  actual: 1500,
  limit: 1200,
  tolerancePercent: 20,
};

const open = (reason: string) => userEvent.click(screen.getByRole("button", { name: `Ver vista previa: ${reason}` }));
const preview = () => screen.getByRole("complementary", { name: "Vista previa de la alerta" });

beforeEach(() => {
  useLoad.mockClear();
  details = {
    "1": { typeCode: "PERFIL_EXCEDIDO", evidence: {} },
    "2": { typeCode: "ORIGEN_DISTINTO", evidence: {} },
    "3": { typeCode: "HORARIO_NOCTURNO", evidence: {} },
    "4": { typeCode: "PERFIL_EXCEDIDO", evidence: TRACKED },
  };
});

describe("vista previa de la alerta (VDI-138)", () => {
  it("pide el detalle de la alerta que se abre", async () => {
    render(<AlertsView />);
    await open(ALERTS[3].reason);
    expect(useLoad.mock.calls.some(([key]) => key === "alerta-4")).toBe(true);
  });

  it("una alerta del seguimiento del perfil muestra qué se rompió, lo declarado, lo real, el límite y la tolerancia", async () => {
    render(<AlertsView />);
    await open(ALERTS[3].reason);
    const panel = within(preview());
    expect(panel.getByText("Aspecto del perfil que se rompió")).toBeInTheDocument();
    expect(panel.getAllByText("Ingresos por encima del rango mensual declarado").length).toBeGreaterThan(0);
    expect(panel.getByText("USD 1,000")).toBeInTheDocument();
    expect(panel.getByText("USD 1,500")).toBeInTheDocument();
    expect(panel.getByText("USD 1,200")).toBeInTheDocument();
    expect(panel.getByText("20 %")).toBeInTheDocument();
  });

  it.each([
    [0, "Movimientos por encima de lo declarado en el perfil"],
    [1, "Dinero recibido de un tipo que no declaró"],
  ])("una alerta de perfil sin evidencia (%#) dice de qué trata y que falta el detalle", async (index, aspect) => {
    render(<AlertsView />);
    await open(ALERTS[index].reason);
    const panel = within(preview());
    expect(panel.getByText(aspect)).toBeInTheDocument();
    expect(panel.getByText(/no trae el detalle de la desviación/)).toBeInTheDocument();
  });

  it("una alerta de otro tipo no muestra la sección del perfil", async () => {
    render(<AlertsView />);
    await open(ALERTS[2].reason);
    expect(within(preview()).queryByText("Aspecto del perfil que se rompió")).not.toBeInTheDocument();
  });

  it("mientras el detalle no llega muestra el resumen sin la sección del perfil y sin romperse", async () => {
    details = {};
    render(<AlertsView />);
    await open(ALERTS[0].reason);
    const panel = within(preview());
    expect(panel.getByText(ALERTS[0].reason)).toBeInTheDocument();
    expect(panel.queryByText("Aspecto del perfil que se rompió")).not.toBeInTheDocument();
  });

  it("Cerrar la vista previa la quita", async () => {
    render(<AlertsView />);
    await open(ALERTS[0].reason);
    await userEvent.click(screen.getByRole("button", { name: "Cerrar vista previa" }));
    expect(screen.queryByRole("complementary", { name: "Vista previa de la alerta" })).not.toBeInTheDocument();
  });
});
