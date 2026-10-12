import { act, render, renderHook, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Alert, ConsoleUser } from "@/lib/console/api";
import { signOut, startSession } from "@/lib/console/session";
import { ConsoleProvider, useConsole, WithSession } from "./ConsoleProvider";

const replace = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }) }));

const user: ConsoleUser = {
  id: "u1",
  email: "a@b.c",
  fullName: "Ana Pérez",
  initials: null,
  jobTitle: null,
  role: "FRAUD_ANALYST",
  active: true,
};

const alert = (id: string, status: Alert["status"]): Alert => ({
  id,
  account: "123",
  reason: "motivo",
  criticality: "HIGH",
  status,
  assigneeId: null,
  assigneeName: null,
  raisedAt: "2026-10-06T04:08:03Z",
});

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

beforeEach(() => {
  act(() => signOut());
  replace.mockClear();
});
afterEach(() => vi.unstubAllGlobals());

describe("useConsole", () => {
  it("falla fuera del proveedor", () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    expect(() => renderHook(() => useConsole())).toThrow(/ConsoleProvider/);
    vi.restoreAllMocks();
  });
});

describe("ConsoleProvider", () => {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <ConsoleProvider user={user} token="tok">
      {children}
    </ConsoleProvider>
  );

  beforeEach(() => {
    act(() => startSession({ accessToken: "tok", expiresIn: 3600, tokenType: "Bearer", user }));
  });

  it("carga las alertas y expone al usuario y el token", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json([alert("a1", "UNASSIGNED")])));
    const { result } = renderHook(() => useConsole(), { wrapper });

    await waitFor(() => expect(result.current.alerts.data).toHaveLength(1));
    expect(result.current.user.fullName).toBe("Ana Pérez");
    expect(result.current.token).toBe("tok");
  });

  it("takeAlert reemplaza la alerta en la lista con la respuesta del servidor", async () => {
    const taken = { ...alert("a1", "ASSIGNED"), assigneeName: "Ana Pérez" };
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(json([alert("a1", "UNASSIGNED"), alert("a2", "UNASSIGNED")]))
      .mockResolvedValueOnce(json(taken));
    vi.stubGlobal("fetch", fetchMock);
    const { result } = renderHook(() => useConsole(), { wrapper });
    await waitFor(() => expect(result.current.alerts.data).toHaveLength(2));

    await act(() => result.current.takeAlert("a1"));

    expect(fetchMock.mock.calls[1][0]).toMatch(/\/alerts\/a1\/take$/);
    expect(fetchMock.mock.calls[1][1].method).toBe("POST");
    expect(result.current.alerts.data?.map((a) => a.status)).toEqual(["ASSIGNED", "UNASSIGNED"]);
  });

  it("si otra persona la tomó (409) vuelve a cargar la lista y propaga el error", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(json([alert("a1", "UNASSIGNED")]))
      .mockResolvedValueOnce(json({ title: "Conflicto" }, 409))
      .mockResolvedValueOnce(json([alert("a1", "ASSIGNED")]));
    vi.stubGlobal("fetch", fetchMock);
    const { result } = renderHook(() => useConsole(), { wrapper });
    await waitFor(() => expect(result.current.alerts.data).toHaveLength(1));

    await act(async () => {
      await expect(result.current.takeAlert("a1")).rejects.toMatchObject({ status: 409 });
    });

    await waitFor(() => expect(result.current.alerts.data?.[0].status).toBe("ASSIGNED"));
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it("un 401 al tomar la alerta cierra la sesión", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(json([alert("a1", "UNASSIGNED")]))
        .mockResolvedValueOnce(json({ title: "Auth" }, 401)),
    );
    const { result } = renderHook(() => useConsole(), { wrapper });
    await waitFor(() => expect(result.current.alerts.data).toHaveLength(1));

    await act(async () => {
      await expect(result.current.takeAlert("a1")).rejects.toMatchObject({ status: 401 });
    });

    expect(sessionStorage.getItem("console.session")).toBeNull();
  });
});

describe("WithSession", () => {
  it("sin sesión no muestra nada y manda al login", async () => {
    render(<WithSession>contenido</WithSession>);
    expect(screen.queryByText("contenido")).not.toBeInTheDocument();
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login"));
  });

  it("con sesión muestra el contenido", async () => {
    act(() => startSession({ accessToken: "tok", expiresIn: 3600, tokenType: "Bearer", user }));
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json([])));
    render(<WithSession>contenido</WithSession>);
    expect(await screen.findByText("contenido")).toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();
  });
});
