import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "./api";
import { changeError, changeProfileTolerance, fetchProfileTolerance, validateTolerance } from "./profileTolerance";

afterEach(() => vi.unstubAllGlobals());

const LIMITS = { tolerancePercent: 20, minPercent: 0, maxPercent: 45 };

describe("validateTolerance", () => {
  it("acepta un entero dentro del rango y distinto del vigente", () => {
    expect(validateTolerance("25", LIMITS)).toEqual({ value: 25 });
    expect(validateTolerance(" 0 ", LIMITS)).toEqual({ value: 0 });
    expect(validateTolerance("45", LIMITS)).toEqual({ value: 45 });
  });

  it("pide un valor", () => {
    expect(validateTolerance("  ", LIMITS).error).toBe("Escribe la tolerancia.");
  });

  it.each(["2.5", "abc", "-5", "1e1", "2 5"])("rechaza %s porque no es un entero sin signo", (raw) => {
    expect(validateTolerance(raw, LIMITS).error).toMatch(/entero/);
  });

  it("rechaza lo que sale del rango que manda la API", () => {
    expect(validateTolerance("46", LIMITS).error).toBe("Debe estar entre 0 y 45.");
    expect(validateTolerance("5", { ...LIMITS, minPercent: 10 }).error).toBe("Debe estar entre 10 y 45.");
  });

  it("rechaza la tolerancia que ya está vigente", () => {
    expect(validateTolerance("20", LIMITS).error).toMatch(/vigente/);
  });
});

describe("llamadas a la API", () => {
  const ok = (body: unknown) =>
    new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });

  it("pide la tolerancia con el token", async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok({}));
    vi.stubGlobal("fetch", fetchMock);
    await fetchProfileTolerance("tok");
    expect(fetchMock.mock.calls[0][0]).toMatch(/\/api\/console\/admin\/profile-tolerance$/);
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe("Bearer tok");
  });

  it("cambia la tolerancia con PUT", async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok({}));
    vi.stubGlobal("fetch", fetchMock);
    await changeProfileTolerance("tok", 30);
    expect(fetchMock.mock.calls[0][1].method).toBe("PUT");
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ tolerancePercent: 30 });
  });
});

describe("changeError", () => {
  it("usa el mensaje del campo que manda la API", () => {
    const e = new ApiError(400, "Bad", undefined, undefined, { tolerancePercent: "Debe ser de 0 a 45" });
    expect(changeError(e)).toBe("Debe ser de 0 a 45");
  });

  it.each([
    [new ApiError(400, "Bad"), /no aceptó/],
    [new ApiError(403, "No"), /administrador/],
    [new ApiError(0, "Net"), /conectar/],
    [new ApiError(500, "Boom"), /Intenta de nuevo/],
    [new Error("x"), /Intenta de nuevo/],
  ])("mensaje general %#", (error, expected) => {
    expect(changeError(error)).toMatch(expected);
  });
});
