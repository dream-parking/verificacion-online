import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { LoginResponse } from "./api";

const response: LoginResponse = {
  accessToken: "tok",
  expiresIn: 3600,
  tokenType: "Bearer",
  user: { id: "u1", email: "a@b.c", fullName: "Ana", initials: "A", jobTitle: null, role: "ADMIN", active: true },
};

// El módulo guarda la sesión en una variable: se importa de nuevo en cada prueba para empezar limpio.
async function load() {
  vi.resetModules();
  return import("./session");
}

afterEach(() => vi.restoreAllMocks());

describe("sesión", () => {
  it("sin nada guardado no hay sesión", async () => {
    const { useSession } = await load();
    const { result } = renderHook(() => useSession());
    expect(result.current).toBeNull();
  });

  it("startSession guarda la sesión y avisa a los suscriptores", async () => {
    const { startSession, useSession } = await load();
    const { result } = renderHook(() => useSession());

    act(() => startSession(response));

    expect(result.current?.accessToken).toBe("tok");
    expect(result.current?.user.fullName).toBe("Ana");
    expect(JSON.parse(sessionStorage.getItem("console.session")!).accessToken).toBe("tok");
  });

  it("recupera la sesión guardada al recargar", async () => {
    const stored = { accessToken: "old", expiresAt: Date.now() + 60_000, user: response.user };
    sessionStorage.setItem("console.session", JSON.stringify(stored));
    const { useSession } = await load();
    expect(renderHook(() => useSession()).result.current?.accessToken).toBe("old");
  });

  it("descarta la sesión guardada si ya venció", async () => {
    const expired = { accessToken: "old", expiresAt: Date.now() - 1, user: response.user };
    sessionStorage.setItem("console.session", JSON.stringify(expired));
    const { useSession } = await load();
    expect(renderHook(() => useSession()).result.current).toBeNull();
  });

  it("ignora un valor guardado que no es JSON", async () => {
    sessionStorage.setItem("console.session", "{no es json");
    const { useSession } = await load();
    expect(renderHook(() => useSession()).result.current).toBeNull();
  });

  it("borra la sesión antigua de localStorage", async () => {
    localStorage.setItem("consola.sesion", "x");
    const { useSession } = await load();
    renderHook(() => useSession());
    expect(localStorage.getItem("consola.sesion")).toBeNull();
  });

  it("signOut limpia el almacenamiento y avisa", async () => {
    const { startSession, signOut, useSession } = await load();
    const { result } = renderHook(() => useSession());
    act(() => startSession(response));

    act(() => signOut());

    expect(result.current).toBeNull();
    expect(sessionStorage.getItem("console.session")).toBeNull();
  });

  it("la sesión sigue en memoria aunque el almacenamiento falle", async () => {
    const { startSession, useSession } = await load();
    const { result } = renderHook(() => useSession());
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("bloqueado");
    });

    act(() => startSession(response));

    expect(result.current?.accessToken).toBe("tok");
  });
});
