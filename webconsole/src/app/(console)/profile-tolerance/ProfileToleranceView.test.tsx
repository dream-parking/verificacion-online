import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, type ConsoleUser } from "@/lib/console/api";
import type { ProfileTolerance } from "@/lib/console/profileTolerance";
import { ProfileToleranceView } from "./ProfileToleranceView";

let user: ConsoleUser;
vi.mock("@/components/console/ConsoleProvider", () => ({ useConsole: () => ({ user, token: "tok" }) }));

type Load = { data?: ProfileTolerance; error?: ApiError; loading: boolean };
let load: Load;
const reload = vi.fn();
const update = vi.fn();
const useLoad = vi.fn();
vi.mock("@/lib/console/hooks", async (original) => ({
  ...(await original<typeof import("@/lib/console/hooks")>()),
  useLoad: (...args: unknown[]) => {
    useLoad(...args);
    return { ...load, reload, update };
  },
}));

const TOLERANCE: ProfileTolerance = {
  tolerancePercent: 20,
  minPercent: 0,
  maxPercent: 45,
  changes: [
    { changedAt: "2026-10-28T15:00:00Z", changedBy: "Ana Pérez", previousPercent: 15, tolerancePercent: 20 },
    { changedAt: "2026-10-01T15:00:00Z", changedBy: null, previousPercent: null, tolerancePercent: 20 },
  ],
};

const makeUser = (role: ConsoleUser["role"]): ConsoleUser => ({
  id: "u1",
  email: "a@example.com",
  fullName: "Ana Pérez",
  initials: null,
  jobTitle: null,
  role,
  active: true,
});

beforeEach(() => {
  user = makeUser("ADMIN");
  load = { data: TOLERANCE, loading: false };
  useLoad.mockClear();
  reload.mockClear();
  update.mockClear();
});

afterEach(() => vi.unstubAllGlobals());

describe("ProfileToleranceView", () => {
  it("un administrador pide la tolerancia y ve el formulario y la bitácora", () => {
    render(<ProfileToleranceView />);
    expect(useLoad.mock.calls[0][0]).toBe("tolerancia-perfil");
    expect(screen.getByLabelText("Tolerancia (%)")).toHaveValue("20");
    const log = screen.getByRole("table", { name: "Bitácora de cambios de la tolerancia" });
    expect(within(log).getByText("Ana Pérez")).toBeInTheDocument();
    expect(within(log).getByText("Valor inicial")).toBeInTheDocument();
    expect(screen.queryByText("Solo administradores.")).not.toBeInTheDocument();
  });

  it.each(["KYC_LEAD", "FRAUD_ANALYST"] as const)(
    "el rol %s ve el aviso, no pide la tolerancia y no muestra el formulario",
    (role) => {
      user = makeUser(role);
      load = { loading: false };
      render(<ProfileToleranceView />);
      expect(screen.getByText("Solo administradores.")).toBeInTheDocument();
      expect(screen.getByText(/Tu rol no puede ver ni cambiar la tolerancia/)).toBeInTheDocument();
      // A null key means useLoad never calls the API (it would answer 403).
      expect(useLoad.mock.calls[0][0]).toBeNull();
      expect(screen.queryByLabelText("Tolerancia (%)")).not.toBeInTheDocument();
      expect(screen.queryByRole("table")).not.toBeInTheDocument();
    },
  );

  it("mientras carga muestra el esqueleto y ningún formulario", () => {
    load = { loading: true };
    render(<ProfileToleranceView />);
    expect(screen.getByRole("status", { name: "Cargando tolerancia" })).toBeInTheDocument();
    expect(screen.queryByLabelText("Tolerancia (%)")).not.toBeInTheDocument();
  });

  it("si falla la carga explica el error y deja reintentar", async () => {
    load = { error: new ApiError(500, "Boom"), loading: false };
    render(<ProfileToleranceView />);
    expect(screen.getByText("No pudimos cargar la tolerancia")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Reintentar" }));
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it("al guardar avisa con el valor nuevo y actualiza lo cargado con la respuesta del API", async () => {
    const next: ProfileTolerance = { ...TOLERANCE, tolerancePercent: 30 };
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify(next), { status: 200, headers: { "Content-Type": "application/json" } }),
      ),
    );
    render(<ProfileToleranceView />);
    await userEvent.clear(screen.getByLabelText("Tolerancia (%)"));
    await userEvent.type(screen.getByLabelText("Tolerancia (%)"), "30");
    await userEvent.click(screen.getByRole("button", { name: "Guardar tolerancia" }));

    expect(await screen.findByRole("status")).toHaveTextContent("La tolerancia ahora es 30 %");
    expect(update).toHaveBeenCalledTimes(1);
    expect(update.mock.calls[0][0]()).toEqual(next);

    await userEvent.click(screen.getByRole("button", { name: "Cerrar aviso" }));
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });
});
