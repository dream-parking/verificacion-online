import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { DossiersView } from "./DossiersView";

vi.mock("@/components/console/ConsoleProvider", () => ({ useConsole: () => ({ token: "tok" }) }));
const signOut = vi.fn();
vi.mock("@/lib/console/session", () => ({ signOut: () => signOut() }));

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });

const archived = (requestNumber: string) => ({
  requestNumber,
  customerNumber: "CLI-0000001",
  dossier: {
    id: Number(requestNumber.slice(-1)),
    requestId: "11111111-1111-1111-1111-111111111111",
    frozenAt: "2026-10-28T15:00:00Z",
    retainedUntil: "2036-10-28T15:00:00Z",
    signature: { contractVersion: "2026.1", contractFingerprint: "ab12", signedAt: "2026-10-28T15:00:00Z" },
    evidence: null,
  },
});

const field = (label: string) => screen.getByLabelText(label);
const search = () => userEvent.click(screen.getByRole("button", { name: "Buscar expediente" }));

afterEach(() => {
  vi.unstubAllGlobals();
  signOut.mockClear();
});

describe("DossiersView", () => {
  it("avisa que cada consulta queda en la auditoría de accesos", () => {
    render(<DossiersView />);
    expect(screen.getByText(/cada consulta queda en la auditoría de accesos/)).toBeInTheDocument();
  });

  it("un número con mal formato no llama a la API", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    render(<DossiersView />);
    await userEvent.type(field("Número de solicitud"), "SOL-1");
    await search();
    expect(screen.getByRole("alert")).toHaveTextContent("El número de solicitud tiene el formato SOL-2026-00001.");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("busca por número de solicitud y muestra el expediente con un enlace a la solicitud", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json(archived("SOL-2026-00001"))));
    render(<DossiersView />);
    await userEvent.type(field("Número de solicitud"), "sol-2026-00001");
    await search();
    expect(await screen.findByRole("region", { name: "Expediente de SOL-2026-00001" })).toBeInTheDocument();
    expect(screen.getByText("2026.1")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Ver la solicitud" })).toHaveAttribute(
      "href",
      "/requests/11111111-1111-1111-1111-111111111111",
    );
  });

  it("por número de cliente muestra un expediente por solicitud firmada", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json([archived("SOL-2026-00002"), archived("SOL-2026-00001")])));
    render(<DossiersView />);
    await userEvent.click(screen.getByRole("button", { name: "Número de cliente" }));
    await userEvent.type(field("Número de cliente"), "CLI-0000001");
    await search();
    expect(await screen.findByRole("region", { name: "Expediente de SOL-2026-00002" })).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Expediente de SOL-2026-00001" })).toBeInTheDocument();
  });

  it("un cliente que no ha firmado no tiene expedientes", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json([])));
    render(<DossiersView />);
    await userEvent.click(screen.getByRole("button", { name: "Número de cliente" }));
    await userEvent.type(field("Número de cliente"), "CLI-0000009");
    await search();
    expect(await screen.findByText("Sin expedientes")).toBeInTheDocument();
    expect(screen.getByText(/CLI-0000009 todavía no ha firmado/)).toBeInTheDocument();
  });

  it("explica el 404 de una solicitud que aún no firmó", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json({ title: "Not Found" }, 404)));
    render(<DossiersView />);
    await userEvent.type(field("Número de solicitud"), "SOL-2026-00005");
    await search();
    expect(await screen.findByRole("alert")).toHaveTextContent("todavía no firmó el contrato");
  });

  it("al cambiar de tipo de búsqueda se limpia el resultado anterior", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json(archived("SOL-2026-00001"))));
    render(<DossiersView />);
    await userEvent.type(field("Número de solicitud"), "SOL-2026-00001");
    await search();
    await screen.findByRole("region", { name: "Expediente de SOL-2026-00001" });
    await userEvent.click(screen.getByRole("button", { name: "Número de cliente" }));
    expect(screen.queryByRole("region", { name: /Expediente de/ })).not.toBeInTheDocument();
    expect(field("Número de cliente")).toHaveValue("");
  });

  it("mientras una búsqueda está pendiente no deja lanzar otra", async () => {
    vi.stubGlobal("fetch", vi.fn().mockReturnValue(new Promise(() => {})));
    render(<DossiersView />);
    await userEvent.type(field("Número de solicitud"), "SOL-2026-00001");
    await search();
    expect(screen.getByRole("button", { name: "Buscando…" })).toBeDisabled();
    expect(field("Número de solicitud")).toBeDisabled();
  });

  it("si se cambia de pestaña con una búsqueda pendiente, su respuesta no aparece en la otra pestaña", async () => {
    let answer: (r: Response) => void = () => {};
    vi.stubGlobal("fetch", vi.fn().mockReturnValue(new Promise<Response>((resolve) => (answer = resolve))));
    render(<DossiersView />);
    await userEvent.type(field("Número de solicitud"), "SOL-2026-00001");
    await search();

    await userEvent.click(screen.getByRole("button", { name: "Número de cliente" }));
    expect(field("Número de cliente")).toBeEnabled();

    await act(async () => answer(json(archived("SOL-2026-00001"))));
    expect(screen.queryByRole("region", { name: /Expediente de/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Buscar expediente" })).toBeEnabled();
  });

  it("con la sesión vencida (401) cierra la sesión", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json({ title: "Unauthorized" }, 401)));
    render(<DossiersView />);
    await userEvent.type(field("Número de solicitud"), "SOL-2026-00001");
    await search();
    await screen.findByRole("alert");
    expect(signOut).toHaveBeenCalledTimes(1);
  });
});
