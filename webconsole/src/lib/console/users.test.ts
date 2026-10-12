import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "./api";
import {
  changePassword,
  changePasswordMessage,
  hasEmoji,
  MAX_PASSWORD_BYTES,
  MIN_PASSWORD_LENGTH,
  passwordError,
  resetPassword,
  resetPasswordMessage,
} from "./users";

afterEach(() => vi.unstubAllGlobals());

describe("hasEmoji", () => {
  it("detecta emojis, banderas y modificadores", () => {
    expect(hasEmoji("clave😀")).toBe(true);
    expect(hasEmoji("🇸🇻")).toBe(true);
    expect(hasEmoji("a‍b")).toBe(true);
  });
  it("permite texto normal, acentos, © y ®", () => {
    expect(hasEmoji("Contraseña-ñ-123")).toBe(false);
    expect(hasEmoji("©®")).toBe(false);
  });
});

describe("passwordError", () => {
  const email = "ana@example.com";

  it("acepta una contraseña válida", () => {
    expect(passwordError("una-clave-larga-1", email)).toBe("");
  });
  it("rechaza emojis antes que cualquier otra regla", () => {
    expect(passwordError("😀", email)).toMatch(/emojis/);
  });
  it("rechaza las demasiado cortas", () => {
    expect(passwordError("a".repeat(MIN_PASSWORD_LENGTH - 1), email)).toMatch(/al menos/);
  });
  it("mide el máximo en bytes, no en caracteres", () => {
    expect(passwordError("a".repeat(MAX_PASSWORD_BYTES), email)).toBe("");
    expect(passwordError("a".repeat(MAX_PASSWORD_BYTES + 1), email)).toMatch(/demasiado larga/);
    // 40 caracteres, 80 bytes.
    expect(passwordError("ñ".repeat(40), email)).toMatch(/demasiado larga/);
  });
  it("rechaza la que es igual al correo, sin importar mayúsculas ni espacios", () => {
    expect(passwordError("  ANA@example.com ", "ana@EXAMPLE.com")).toMatch(/igual al correo/);
  });
});

describe("mensajes de error de la API", () => {
  it.each([
    [new ApiError(400, "Bad", "muy débil"), /muy débil/],
    [new ApiError(400, "Bad"), /Revisa la política/],
    [new ApiError(403, "No"), /administrador/],
    [new ApiError(404, "NF"), /ya no existe/],
    [new ApiError(0, "Net"), /conectar/],
    [new ApiError(500, "Boom"), /Intenta de nuevo/],
    [new Error("x"), /Intenta de nuevo/],
  ])("resetPasswordMessage %#", (error, expected) => {
    expect(resetPasswordMessage(error)).toMatch(expected);
  });

  it.each([
    [new ApiError(400, "Bad", "no coincide"), /no coincide/],
    [new ApiError(400, "Bad"), /contraseña actual/],
    [new ApiError(401, "Auth"), /sesión terminó/],
    [new ApiError(0, "Net"), /conectar/],
    [new ApiError(500, "Boom"), /cambiar la contraseña/],
  ])("changePasswordMessage %#", (error, expected) => {
    expect(changePasswordMessage(error)).toMatch(expected);
  });
});

describe("llamadas a la API", () => {
  it("resetPassword y changePassword envían el cuerpo y el token", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);

    await resetPassword("tok", "u1", "nueva-clave-123");
    await changePassword("tok", "vieja", "nueva-clave-123");

    const [reset, change] = fetchMock.mock.calls;
    expect(reset[0]).toMatch(/\/api\/console\/users\/u1\/password$/);
    expect(JSON.parse(reset[1].body)).toEqual({ newPassword: "nueva-clave-123" });
    expect(reset[1].headers.Authorization).toBe("Bearer tok");
    expect(change[0]).toMatch(/\/api\/console\/auth\/change-password$/);
    expect(JSON.parse(change[1].body)).toEqual({ currentPassword: "vieja", newPassword: "nueva-clave-123" });
  });
});
