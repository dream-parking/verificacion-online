import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, type ScoreModel } from "./api";
import {
  fetchScoreModelVersions,
  publishError,
  publishScoreModelVersion,
  rangesOf,
  thresholdsOf,
  validateThresholds,
} from "./scoreModel";

afterEach(() => vi.unstubAllGlobals());

const CURRENT = { lowMax: 39, mediumMax: 69 };

describe("validateThresholds", () => {
  it("acepta enteros con 0 ≤ Bajo < Medio < 100", () => {
    expect(validateThresholds("30", "60", CURRENT)).toEqual({ values: { lowMax: 30, mediumMax: 60 } });
    expect(validateThresholds(" 0 ", "99")).toEqual({ values: { lowMax: 0, mediumMax: 99 } });
  });

  it("pide los dos valores", () => {
    expect(validateThresholds("", " ").errors).toEqual({
      lowMax: "Escribe el fin del nivel Bajo.",
      mediumMax: "Escribe el fin del nivel Medio.",
    });
  });

  it.each(["39.5", "abc", "1e2", "3 9"])("rechaza %s porque no es entero", (value) => {
    expect(validateThresholds(value, "69").errors?.lowMax).toMatch(/entero/);
  });

  it("rechaza un Bajo menor que 0 y un Medio de 100 o más", () => {
    expect(validateThresholds("-1", "60").errors).toEqual({ lowMax: "No puede ser menor que 0." });
    expect(validateThresholds("10", "100").errors).toEqual({ mediumMax: "Debe ser menor que 100." });
  });

  it("exige que Bajo sea menor que Medio", () => {
    expect(validateThresholds("50", "40").errors).toEqual({ lowMax: "Debe ser menor que el fin del nivel Medio." });
    expect(validateThresholds("50", "50").errors?.lowMax).toMatch(/menor que el fin del nivel Medio/);
  });

  it("no deja publicar los mismos umbrales de la versión vigente", () => {
    expect(validateThresholds("39", "69", CURRENT).errors).toEqual({ form: expect.stringMatching(/mismos umbrales/) });
  });
});

describe("rangesOf y thresholdsOf", () => {
  it("calcula dónde empieza y termina cada nivel", () => {
    expect(rangesOf(CURRENT)).toEqual({
      low: { from: 0, to: 39 },
      medium: { from: 40, to: 69 },
      high: { from: 70, to: 100 },
    });
  });

  it("lee los umbrales del modelo", () => {
    const model = { low: { from: 0, to: 29 }, medium: { from: 30, to: 59 } } as ScoreModel;
    expect(thresholdsOf(model)).toEqual({ lowMax: 29, mediumMax: 59 });
  });
});

describe("llamadas a la API", () => {
  const ok = (body: unknown) =>
    new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });

  it("pide el historial con el token", async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok([]));
    vi.stubGlobal("fetch", fetchMock);
    await fetchScoreModelVersions("tok");
    expect(fetchMock.mock.calls[0][0]).toMatch(/\/api\/console\/admin\/score-model\/versions$/);
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe("Bearer tok");
  });

  it("publica los umbrales con POST", async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok({ version: 3 }));
    vi.stubGlobal("fetch", fetchMock);
    await publishScoreModelVersion("tok", { lowMax: 30, mediumMax: 60 });
    expect(fetchMock.mock.calls[0][1].method).toBe("POST");
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ lowMax: 30, mediumMax: 60 });
  });
});

describe("publishError", () => {
  it("usa los mensajes por campo que manda la API", () => {
    const e = new ApiError(400, "Bad", undefined, undefined, { mediumMax: "Debe ser menor que 100" });
    expect(publishError(e)).toEqual({ lowMax: undefined, mediumMax: "Debe ser menor que 100" });
  });

  it.each([
    [new ApiError(400, "Bad"), /no aceptó/],
    [new ApiError(400, "Bad", undefined, undefined, { other: "x" }), /no aceptó/],
    [new ApiError(403, "No"), /administrador/],
    [new ApiError(409, "Conflict"), /cambió/],
    [new ApiError(0, "Net"), /conectar/],
    [new ApiError(500, "Boom"), /Intenta de nuevo/],
    [new Error("x"), /Intenta de nuevo/],
  ])("mensaje general %#", (error, expected) => {
    expect(publishError(error).form).toMatch(expected);
  });
});
