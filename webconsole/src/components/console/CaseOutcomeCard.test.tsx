import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, type ConsoleUser } from "@/lib/console/api";
import type { Outcome } from "@/lib/console/outcomes";
import { CaseOutcomeCard } from "./CaseOutcomeCard";

let user: ConsoleUser;
vi.mock("./ConsoleProvider", () => ({ useConsole: () => ({ user, token: "tok" }) }));

type Load = { data?: Outcome[]; error?: ApiError; loading: boolean };
let load: Load;
const reload = vi.fn();
const useLoad = vi.fn();
vi.mock("@/lib/console/hooks", async (original) => ({
  ...(await original<typeof import("@/lib/console/hooks")>()),
  useLoad: (...args: unknown[]) => {
    useLoad(...args);
    return { ...load, reload, update: vi.fn() };
  },
}));
const signOut = vi.fn();
vi.mock("@/lib/console/session", () => ({ signOut: () => signOut() }));

const makeUser = (role: ConsoleUser["role"]): ConsoleUser => ({
  id: "u1",
  email: "a@example.com",
  fullName: "Ana Pérez",
  initials: null,
  jobTitle: null,
  role,
  active: true,
});

const outcome = (over: Partial<Outcome> = {}): Outcome => ({
  id: 1,
  requestId: "r1",
  outcome: "FRAUDE_CONFIRMADO",
  reason: "El titular del DUI negó haber abierto la cuenta",
  registeredBy: "Ana Pérez",
  registeredAt: "2026-10-28T15:00:00Z",
  listed: ["DOCUMENT", "PHONE", "DEVICE"],
  revoked: [],
  ...over,
});

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });

beforeEach(() => {
  user = makeUser("FRAUD_ANALYST");
  load = { data: [], loading: false };
  useLoad.mockClear();
  reload.mockClear();
  signOut.mockClear();
});

afterEach(() => vi.unstubAllGlobals());

const dialog = () => screen.getByRole("dialog");

describe("CaseOutcomeCard · sin desenlace", () => {
  it("pide el desenlace de la solicitud y ofrece las dos acciones distintas", () => {
    render(<CaseOutcomeCard requestId="r1" />);
    expect(useLoad.mock.calls[0][0]).toBe("desenlace-r1");
    expect(screen.getByText("Sin desenlace registrado")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Confirmar fraude" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Marcar como falso positivo" })).toBeInTheDocument();
  });

  it("pide confirmación y motivo antes de enviar, y no envía sin motivo", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    render(<CaseOutcomeCard requestId="r1" />);
    await userEvent.click(screen.getByRole("button", { name: "Confirmar fraude" }));
    expect(within(dialog()).getByText(/pasan a la lista interna de fraude confirmado/)).toBeInTheDocument();
    await userEvent.click(within(dialog()).getByRole("button", { name: "Confirmar" }));
    expect(within(dialog()).getByRole("alert")).toHaveTextContent("Escribe por qué se cierra así el caso.");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("un falso positivo avisa que solo se registra", async () => {
    render(<CaseOutcomeCard requestId="r1" />);
    await userEvent.click(screen.getByRole("button", { name: "Marcar como falso positivo" }));
    expect(within(dialog()).getByText(/Solo se registra/)).toBeInTheDocument();
  });

  it("registra el desenlace con su motivo, avisa y vuelve a cargar", async () => {
    const fetchMock = vi.fn().mockResolvedValue(json(outcome(), 201));
    vi.stubGlobal("fetch", fetchMock);
    render(<CaseOutcomeCard requestId="r1" />);
    await userEvent.click(screen.getByRole("button", { name: "Confirmar fraude" }));
    await userEvent.type(within(dialog()).getByLabelText("Motivo"), "El titular negó la apertura");
    await userEvent.click(within(dialog()).getByRole("button", { name: "Confirmar" }));

    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({
      outcome: "FRAUDE_CONFIRMADO",
      reason: "El titular negó la apertura",
    });
    expect(await screen.findByRole("status")).toHaveTextContent("Desenlace registrado: Fraude confirmado.");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it("muestra en español el error del servidor y deja reintentar sin cerrar el diálogo", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json({ title: "Bad", errors: { reason: "El motivo no tiene el formato esperado" } }, 400)));
    render(<CaseOutcomeCard requestId="r1" />);
    await userEvent.click(screen.getByRole("button", { name: "Confirmar fraude" }));
    await userEvent.type(within(dialog()).getByLabelText("Motivo"), "Motivo válido");
    await userEvent.click(within(dialog()).getByRole("button", { name: "Confirmar" }));
    expect(await within(dialog()).findByRole("alert")).toHaveTextContent("El motivo no tiene el formato esperado");
    expect(reload).not.toHaveBeenCalled();
  });

  it("con la sesión vencida (401) cierra la sesión", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json({ title: "Unauthorized" }, 401)));
    render(<CaseOutcomeCard requestId="r1" />);
    await userEvent.click(screen.getByRole("button", { name: "Confirmar fraude" }));
    await userEvent.type(within(dialog()).getByLabelText("Motivo"), "Motivo válido");
    await userEvent.click(within(dialog()).getByRole("button", { name: "Confirmar" }));
    await within(dialog()).findByRole("alert");
    expect(signOut).toHaveBeenCalledTimes(1);
  });

  it("Cancelar cierra el diálogo sin enviar nada", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    render(<CaseOutcomeCard requestId="r1" />);
    await userEvent.click(screen.getByRole("button", { name: "Confirmar fraude" }));
    await userEvent.click(within(dialog()).getByRole("button", { name: "Cancelar" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("CaseOutcomeCard · con desenlace", () => {
  it("muestra el vigente con quién, cuándo, el motivo y los datos que puso en la lista", () => {
    load = { data: [outcome()], loading: false };
    render(<CaseOutcomeCard requestId="r1" />);
    expect(screen.getByText("▲ Fraude confirmado")).toBeInTheDocument();
    expect(screen.getByText(/Ana Pérez, el /)).toBeInTheDocument();
    expect(screen.getByText("El titular del DUI negó haber abierto la cuenta")).toBeInTheDocument();
    expect(screen.getByText(/lista de fraude confirmado: el DUI, el teléfono y el dispositivo/)).toBeInTheDocument();
  });

  it("el vigente es el último y el resto queda en el historial, del más reciente al más antiguo", () => {
    load = {
      data: [
        outcome({ id: 1, outcome: "FRAUDE_CONFIRMADO", reason: "Primero" }),
        outcome({ id: 2, outcome: "FALSO_POSITIVO", reason: "Segundo", listed: [], revoked: ["DOCUMENT", "PHONE", "DEVICE"] }),
      ],
      loading: false,
    };
    render(<CaseOutcomeCard requestId="r1" />);
    expect(screen.getByText("✓ Falso positivo")).toBeInTheDocument();
    expect(screen.getByText(/Sacó de las listas lo que el anterior había puesto: el DUI, el teléfono y el dispositivo/)).toBeInTheDocument();
    expect(screen.getByText("Historial de desenlaces (1)")).toBeInTheDocument();
    expect(screen.getByText("Primero")).toBeInTheDocument();
  });

  it("para corregir ofrece solo el desenlace contrario y avisa que se revocan las entradas de las listas", async () => {
    load = { data: [outcome()], loading: false };
    render(<CaseOutcomeCard requestId="r1" />);
    expect(screen.queryByRole("button", { name: "Confirmar fraude" })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Corregir a falso positivo" }));
    expect(within(dialog()).getByText(/Se sacan de las listas internas el DUI, el teléfono y el dispositivo/)).toBeInTheDocument();
    expect(within(dialog()).getByText(/no se borra: queda en el historial/)).toBeInTheDocument();
    expect(within(dialog()).getByRole("button", { name: "Corregir" })).toBeInTheDocument();
  });

  it("corregir un falso positivo a fraude avisa que pasan a la lista", async () => {
    load = { data: [outcome({ outcome: "FALSO_POSITIVO", listed: [] })], loading: false };
    render(<CaseOutcomeCard requestId="r1" />);
    await userEvent.click(screen.getByRole("button", { name: "Corregir a fraude confirmado" }));
    expect(within(dialog()).getByText(/pasan a la lista interna de fraude confirmado/)).toBeInTheDocument();
  });
});

describe("CaseOutcomeCard · permisos y estados", () => {
  it.each(["KYC_LEAD"] as const)("el rol %s ve el desenlace pero no puede registrarlo ni corregirlo", (role) => {
    user = makeUser(role);
    load = { data: [outcome()], loading: false };
    render(<CaseOutcomeCard requestId="r1" />);
    expect(screen.getByText("▲ Fraude confirmado")).toBeInTheDocument();
    expect(screen.getByText(/Solo los analistas de fraude y los administradores/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Corregir|Confirmar fraude|falso positivo/ })).not.toBeInTheDocument();
  });

  it("el administrador también puede registrar", () => {
    user = makeUser("ADMIN");
    render(<CaseOutcomeCard requestId="r1" />);
    expect(screen.getByRole("button", { name: "Confirmar fraude" })).toBeInTheDocument();
  });

  it("mientras carga muestra el esqueleto", () => {
    load = { loading: true };
    render(<CaseOutcomeCard requestId="r1" />);
    expect(screen.getByRole("status", { name: "Cargando desenlace" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Confirmar fraude" })).not.toBeInTheDocument();
  });

  it("si falla la carga explica el error y deja reintentar", async () => {
    load = { error: new ApiError(500, "Boom"), loading: false };
    render(<CaseOutcomeCard requestId="r1" />);
    expect(screen.getByText("No pudimos cargar el desenlace")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Reintentar" }));
    expect(reload).toHaveBeenCalledTimes(1);
  });
});
