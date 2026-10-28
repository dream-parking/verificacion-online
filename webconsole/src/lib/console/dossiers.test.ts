import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "./api";
import { findDossiers, searchError, validateDossierNumber } from "./dossiers";

afterEach(() => vi.unstubAllGlobals());

describe("validateDossierNumber", () => {
  it("acepta un número de solicitud y lo deja recortado y en mayúsculas", () => {
    expect(validateDossierNumber("request", " sol-2026-00001 ")).toEqual({ value: "SOL-2026-00001" });
    expect(validateDossierNumber("request", "SOL-2026-123456")).toEqual({ value: "SOL-2026-123456" });
  });

  it("acepta un número de cliente", () => {
    expect(validateDossierNumber("customer", "cli-0000001")).toEqual({ value: "CLI-0000001" });
    expect(validateDossierNumber("customer", "CLI-00000012")).toEqual({ value: "CLI-00000012" });
  });

  it("pide el número", () => {
    expect(validateDossierNumber("request", "  ").error).toBe("Escribe el número de solicitud.");
    expect(validateDossierNumber("customer", "").error).toBe("Escribe el número de cliente.");
  });

  it.each(["SOL-26-00001", "SOL-2026-0001", "CLI-0000001", "2026-00001", "SOL-2026-00001 x"])(
    "rechaza %s como número de solicitud",
    (raw) => {
      expect(validateDossierNumber("request", raw).error).toBe("El número de solicitud tiene el formato SOL-2026-00001.");
    },
  );

  it.each(["CLI-123456", "SOL-2026-00001", "0000001"])("rechaza %s como número de cliente", (raw) => {
    expect(validateDossierNumber("customer", raw).error).toBe("El número de cliente tiene el formato CLI-0000001.");
  });

  it("rechaza un número de más de 20 caracteres", () => {
    expect(validateDossierNumber("customer", "CLI-" + "1".repeat(20)).error).toMatch(/formato/);
  });
});

describe("findDossiers", () => {
  const ok = (body: unknown) =>
    new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
  const one = { requestNumber: "SOL-2026-00001", customerNumber: "CLI-0000001", dossier: { id: 1 } };

  it("busca por número de solicitud con el token y devuelve una lista de uno", async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok(one));
    vi.stubGlobal("fetch", fetchMock);
    expect(await findDossiers("tok", "request", "SOL-2026-00001")).toEqual([one]);
    expect(fetchMock.mock.calls[0][0]).toMatch(/\/api\/console\/dossiers\?requestNumber=SOL-2026-00001$/);
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe("Bearer tok");
  });

  it("busca por número de cliente y devuelve la lista tal cual", async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok([one, { ...one, requestNumber: "SOL-2026-00002" }]));
    vi.stubGlobal("fetch", fetchMock);
    const found = await findDossiers("tok", "customer", "CLI-0000001");
    expect(found.map((d) => d.requestNumber)).toEqual(["SOL-2026-00001", "SOL-2026-00002"]);
    expect(fetchMock.mock.calls[0][0]).toMatch(/\?customerNumber=CLI-0000001$/);
  });

  it("un cliente que no ha firmado devuelve una lista vacía", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(ok([])));
    expect(await findDossiers("tok", "customer", "CLI-0000001")).toEqual([]);
  });
});

describe("searchError", () => {
  it("el 404 depende de lo que se buscó", () => {
    expect(searchError(new ApiError(404, "x"), "request")).toMatch(/todavía no firmó/);
    expect(searchError(new ApiError(404, "x"), "customer")).toBe("No hay un cliente con ese número.");
  });

  it.each([
    [new ApiError(400, "x"), /no aceptó/],
    [new ApiError(403, "x"), /rol/],
    [new ApiError(0, "x"), /conectar/],
    [new ApiError(500, "x"), /Intenta de nuevo/],
    [new Error("x"), /Intenta de nuevo/],
  ])("mensaje general %#", (error, expected) => {
    expect(searchError(error, "request")).toMatch(expected);
  });
});
