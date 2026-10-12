import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, type LoginResponse } from "./api";
import { errorMessage, PAGE_SIZES, useLoad, usePaginatedFilters } from "./hooks";
import { signOut, startSession } from "./session";

const login: LoginResponse = {
  accessToken: "tok",
  expiresIn: 3600,
  tokenType: "Bearer",
  user: { id: "u1", email: "a@b.c", fullName: "Ana", initials: null, jobTitle: null, role: "ADMIN", active: true },
};

beforeEach(() => {
  act(() => signOut());
});

describe("useLoad", () => {
  it("no pide nada sin sesión", () => {
    const loader = vi.fn();
    const { result } = renderHook(() => useLoad("k", loader));
    expect(loader).not.toHaveBeenCalled();
    expect(result.current.loading).toBe(false);
  });

  it("carga con el token de la sesión", async () => {
    act(() => startSession(login));
    const loader = vi.fn().mockResolvedValue(["x"]);
    const { result } = renderHook(() => useLoad("k", loader));

    expect(result.current.loading).toBe(true);
    await waitFor(() => expect(result.current.data).toEqual(["x"]));
    expect(loader).toHaveBeenCalledWith("tok");
    expect(result.current.loading).toBe(false);
  });

  it("reload vuelve a pedir los datos", async () => {
    act(() => startSession(login));
    const loader = vi.fn().mockResolvedValueOnce(1).mockResolvedValueOnce(2);
    const { result } = renderHook(() => useLoad("k", loader));
    await waitFor(() => expect(result.current.data).toBe(1));

    act(() => result.current.reload());

    await waitFor(() => expect(result.current.data).toBe(2));
  });

  it("update cambia los datos ya cargados sin pedirlos otra vez", async () => {
    act(() => startSession(login));
    const loader = vi.fn<(token: string) => Promise<number[]>>().mockResolvedValue([1, 2]);
    const { result } = renderHook(() => useLoad("k", loader));
    await waitFor(() => expect(result.current.data).toEqual([1, 2]));

    act(() => result.current.update((d) => [...d, 3]));

    expect(result.current.data).toEqual([1, 2, 3]);
    expect(loader).toHaveBeenCalledTimes(1);
  });

  it("un error de la API se expone y no cierra la sesión", async () => {
    act(() => startSession(login));
    const { result } = renderHook(() => useLoad("k", () => Promise.reject(new ApiError(403, "No"))));
    await waitFor(() => expect(result.current.error?.status).toBe(403));
    expect(sessionStorage.getItem("console.session")).not.toBeNull();
  });

  it("un 401 cierra la sesión", async () => {
    act(() => startSession(login));
    const { result } = renderHook(() => useLoad("k", () => Promise.reject(new ApiError(401, "Auth"))));
    await waitFor(() => expect(sessionStorage.getItem("console.session")).toBeNull());
    // Sin sesión ya no hay carga en curso: la consola vuelve al login en vez de mostrar un error.
    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBeUndefined();
  });

  it("un error que no es de la API se envuelve con status 0", async () => {
    act(() => startSession(login));
    const { result } = renderHook(() => useLoad("k", () => Promise.reject(new Error("boom"))));
    await waitFor(() => expect(result.current.error).toBeInstanceOf(ApiError));
    expect(result.current.error?.status).toBe(0);
  });
});

describe("errorMessage", () => {
  it("elige el texto según el status", () => {
    expect(errorMessage(new ApiError(0, "x"), "f")).toMatch(/conexión/);
    expect(errorMessage(new ApiError(403, "x"), "f")).toMatch(/rol/);
    expect(errorMessage(new ApiError(404, "x"), "f")).toMatch(/No encontramos/);
    expect(errorMessage(new ApiError(500, "x"), "f")).toBe("f");
    expect(errorMessage(undefined, "f")).toBe("f");
  });
});

describe("usePaginatedFilters", () => {
  const rows = Array.from({ length: 12 }, (_, i) => i);

  it("pagina con el tamaño inicial", () => {
    const { result } = renderHook(() => usePaginatedFilters({ q: "" }));
    const p = result.current.paginate(rows);
    expect(p).toMatchObject({ page: 1, total: 12, totalPages: Math.ceil(12 / PAGE_SIZES[0]) });
    expect(p.visibleItems).toEqual(rows.slice(0, PAGE_SIZES[0]));
  });

  it("cambiar un filtro o el tamaño vuelve a la primera página", () => {
    const { result } = renderHook(() => usePaginatedFilters({ q: "" }));
    act(() => result.current.setPage(3));
    expect(result.current.paginate(rows).page).toBe(3);

    act(() => result.current.setFilter("q", "a"));
    expect(result.current.paginate(rows).page).toBe(1);

    act(() => result.current.setPage(2));
    act(() => result.current.setSize(10));
    expect(result.current.paginate(rows)).toMatchObject({ page: 1, totalPages: 2 });
  });

  it("limita la página a la última cuando hay menos resultados", () => {
    const { result } = renderHook(() => usePaginatedFilters({ q: "" }));
    act(() => result.current.setPage(3));
    expect(result.current.paginate([1, 2]).page).toBe(1);
    expect(result.current.paginate([]).totalPages).toBe(1);
  });

  it("hasFilters y clear", () => {
    const { result } = renderHook(() => usePaginatedFilters({ q: "", s: "all" }));
    expect(result.current.hasFilters).toBe(false);
    act(() => result.current.setFilter("s", "open"));
    expect(result.current.hasFilters).toBe(true);
    act(() => result.current.clear());
    expect(result.current.hasFilters).toBe(false);
    expect(result.current.filters).toEqual({ q: "", s: "all" });
  });
});
