import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, apiFetch, fetchAllRequests, fetchDocumentPhoto, login, type Page, type RequestSummary } from "./api";

const json = (body: unknown, init: ResponseInit = {}) =>
  new Response(JSON.stringify(body), { headers: { "Content-Type": "application/json" }, ...init });

afterEach(() => vi.unstubAllGlobals());

// Espera que la promesa falle y devuelve el error ya tipado.
const failure = (promise: Promise<unknown>) =>
  promise.then(
    () => {
      throw new Error("Se esperaba que fallara");
    },
    (e: ApiError) => e,
  );

describe("apiFetch", () => {
  it("envía Accept, token y cuerpo JSON", async () => {
    const fetchMock = vi.fn().mockResolvedValue(json({ ok: true }));
    vi.stubGlobal("fetch", fetchMock);

    const result = await apiFetch("/x", { token: "abc", method: "POST", body: { a: 1 } });

    expect(result).toEqual({ ok: true });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/x$/);
    expect(init.method).toBe("POST");
    expect(init.headers).toMatchObject({
      Accept: "application/json",
      "Content-Type": "application/json",
      Authorization: "Bearer abc",
    });
    expect(init.body).toBe('{"a":1}');
  });

  it("no manda Content-Type ni Authorization si no hay cuerpo ni token", async () => {
    const fetchMock = vi.fn().mockResolvedValue(json({}));
    vi.stubGlobal("fetch", fetchMock);
    await apiFetch("/x");
    const { headers, body } = fetchMock.mock.calls[0][1];
    expect(headers).not.toHaveProperty("Content-Type");
    expect(headers).not.toHaveProperty("Authorization");
    expect(body).toBeUndefined();
  });

  it("devuelve undefined en 204", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 204 })));
    await expect(apiFetch("/x")).resolves.toBeUndefined();
  });

  it("convierte un problem+json en ApiError con Retry-After", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          json({ title: "Demasiados intentos", detail: "Espera" }, { status: 429, headers: { "Retry-After": "30" } }),
        ),
    );
    const error = await failure(apiFetch("/x"));
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 429, title: "Demasiados intentos", detail: "Espera", retryAfterSeconds: 30 });
    expect(error.message).toBe("Espera");
  });

  it("usa el statusText si el cuerpo del error no es JSON", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("<html>", { status: 502, statusText: "Bad Gateway" })));
    const error = await failure(apiFetch("/x"));
    expect(error).toMatchObject({ status: 502, title: "Bad Gateway", retryAfterSeconds: undefined });
  });

  it("un fallo de red es un ApiError con status 0", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("fail")));
    await expect(apiFetch("/x")).rejects.toMatchObject({ status: 0, title: "Sin conexión" });
  });
});

describe("login", () => {
  it("hace POST con correo y contraseña", async () => {
    const fetchMock = vi.fn().mockResolvedValue(json({ accessToken: "t" }));
    vi.stubGlobal("fetch", fetchMock);
    await login("a@b.c", "pw");
    expect(fetchMock.mock.calls[0][0]).toMatch(/\/api\/console\/auth\/login$/);
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ email: "a@b.c", password: "pw" });
  });
});

describe("fetchAllRequests", () => {
  const row = (id: string) => ({ id }) as RequestSummary;
  const page = (n: number, totalPages: number, ids: string[]): Page<RequestSummary> => ({
    content: ids.map(row),
    page: n,
    size: 100,
    totalElements: 0,
    totalPages,
  });

  it("recorre todas las páginas y las junta en orden", async () => {
    const fetchMock = vi.fn().mockImplementation(async (url: string) => {
      const p = Number(new URL(url).searchParams.get("page"));
      return json(page(p, 3, [`r${p}`]));
    });
    vi.stubGlobal("fetch", fetchMock);

    const all = await fetchAllRequests("tok");

    expect(all.map((r) => r.id)).toEqual(["r0", "r1", "r2"]);
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it("con una sola página no pide más", async () => {
    const fetchMock = vi.fn().mockResolvedValue(json(page(0, 1, ["a", "b"])));
    vi.stubGlobal("fetch", fetchMock);
    expect(await fetchAllRequests("tok")).toHaveLength(2);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("sin resultados devuelve una lista vacía", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json(page(0, 0, []))));
    expect(await fetchAllRequests("tok")).toEqual([]);
  });
});

describe("fetchDocumentPhoto", () => {
  it("pide la foto del lado indicado con el token y devuelve la imagen", async () => {
    const image = new Blob(["img"], { type: "image/jpeg" });
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, blob: async () => image });
    vi.stubGlobal("fetch", fetchMock);

    const blob = await fetchDocumentPhoto("a b", "FRONT", "tok");

    const [url, init] = fetchMock.mock.calls[0];
    expect(url.endsWith("/api/console/requests/a%20b/identity-document/FRONT")).toBe(true);
    expect(init.headers).toEqual({ Authorization: "Bearer tok" });
    expect(blob).toBe(image);
  });

  it("si el servidor la rechaza lanza el error con su título y detalle", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json({ title: "No encontrada", detail: "Sin foto" }, { status: 404 })));
    const error = await failure(fetchDocumentPhoto("r1", "BACK", "tok"));
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 404, title: "No encontrada", detail: "Sin foto" });
  });

  it("si la respuesta de error no es JSON usa el texto del estado", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("boom", { status: 500, statusText: "Server Error" })));
    const error = await failure(fetchDocumentPhoto("r1", "BACK", "tok"));
    expect(error).toMatchObject({ status: 500, title: "Server Error" });
  });

  it("sin conexión lanza un ApiError de estado 0", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("network")));
    const error = await failure(fetchDocumentPhoto("r1", "FRONT", "tok"));
    expect(error).toMatchObject({ status: 0, title: "Sin conexión" });
  });
});
