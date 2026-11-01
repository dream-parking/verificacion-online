import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "./api";
import { fetchOutcomes, listedText, OUTCOME, outcomeError, registerOutcome, validateReason } from "./outcomes";

afterEach(() => vi.unstubAllGlobals());

describe("validateReason", () => {
  it("acepta un motivo con letras, números y puntuación común, y lo recorta", () => {
    expect(validateReason("  El titular del DUI negó haber abierto la cuenta  ")).toEqual({
      value: "El titular del DUI negó haber abierto la cuenta",
    });
    expect(validateReason("Llamada 2026-10-28: ¿confirmado? (sí)").value).toBe("Llamada 2026-10-28: ¿confirmado? (sí)");
  });

  it("lo pide cuando está vacío o en blanco", () => {
    expect(validateReason("").error).toBe("Escribe por qué se cierra así el caso.");
    expect(validateReason("   ").error).toBe("Escribe por qué se cierra así el caso.");
  });

  it("rechaza más de 150 caracteres y acepta exactamente 150", () => {
    expect(validateReason("a".repeat(151)).error).toBe("Usa 150 caracteres o menos.");
    expect(validateReason("a".repeat(150)).value).toHaveLength(150);
  });

  it.each(["<script>", "motivo {raro}", "emoji 😀", "...", "- - -"])("rechaza %s", (raw) => {
    expect(validateReason(raw).error).toBe("Usa letras, números y signos de puntuación comunes.");
  });
});

describe("listedText", () => {
  it("nombra los datos en español", () => {
    expect(listedText([])).toBe("");
    expect(listedText(["DOCUMENT"])).toBe("el DUI");
    expect(listedText(["DOCUMENT", "PHONE"])).toBe("el DUI y el teléfono");
    expect(listedText(["DOCUMENT", "PHONE", "DEVICE"])).toBe("el DUI, el teléfono y el dispositivo");
  });
});

describe("OUTCOME", () => {
  it("distingue el fraude del falso positivo por texto y por color", () => {
    expect(OUTCOME.FRAUDE_CONFIRMADO.cls).not.toBe(OUTCOME.FALSO_POSITIVO.cls);
    expect(OUTCOME.FRAUDE_CONFIRMADO.label).toMatch(/Fraude confirmado/);
    expect(OUTCOME.FALSO_POSITIVO.label).toMatch(/Falso positivo/);
  });
});

describe("llamadas a la API", () => {
  const ok = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

  it("pide los desenlaces de la solicitud con el token", async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok([]));
    vi.stubGlobal("fetch", fetchMock);
    await fetchOutcomes("tok", "abc-123");
    expect(fetchMock.mock.calls[0][0]).toMatch(/\/api\/console\/requests\/abc-123\/outcome$/);
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe("Bearer tok");
  });

  it("registra el desenlace con POST y su motivo", async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok({ id: 1 }, 201));
    vi.stubGlobal("fetch", fetchMock);
    await registerOutcome("tok", "abc-123", "FALSO_POSITIVO", "Era el mismo titular");
    expect(fetchMock.mock.calls[0][1].method).toBe("POST");
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ outcome: "FALSO_POSITIVO", reason: "Era el mismo titular" });
  });
});

describe("outcomeError", () => {
  it("usa el mensaje en español que manda la API con un 400", () => {
    const e = new ApiError(400, "Bad", undefined, undefined, { reason: "El motivo no tiene el formato esperado" });
    expect(outcomeError(e)).toBe("El motivo no tiene el formato esperado");
  });

  it.each([
    [new ApiError(400, "Bad"), /no aceptó/],
    [new ApiError(403, "No"), /analistas de fraude/],
    [new ApiError(404, "Nada"), /ya no existe/],
    [new ApiError(0, "Net"), /conectar/],
    [new ApiError(500, "Boom"), /Intenta de nuevo/],
    [new Error("x"), /Intenta de nuevo/],
  ])("mensaje general %#", (error, expected) => {
    expect(outcomeError(error)).toMatch(expected);
  });
});
