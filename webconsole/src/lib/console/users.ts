// Console user management (ADMIN only). Contract: docs/openapi.json, POST /users/{id}/password.

import { ApiError, apiFetch } from "./api";

export const MIN_PASSWORD_LENGTH = 10;
/** The API limits the password to 72 bytes (bcrypt limit): accents and emojis take more than one. */
export const MAX_PASSWORD_BYTES = 72;

export const PASSWORD_POLICY = `De ${MIN_PASSWORD_LENGTH} a ${MAX_PASSWORD_BYTES} caracteres, sin emojis y distinta del correo.`;

// Emojis, flags, skin tones and the characters that glue them together (zero-width joiner, variation
// selector, keycap). © and ® are allowed. Built with RegExp because the build target (ES2017) does
// not accept \p{...} in a regex literal.
const EMOJI = new RegExp("(?![\\u00a9\\u00ae])[\\p{Extended_Pictographic}\\p{Regional_Indicator}\\u200d\\ufe0f\\u20e3]", "u");

export const hasEmoji = (text: string) => EMOJI.test(text);

/** Message of the first rule the password breaks, or "" if it is valid. The API validates it again. */
export function passwordError(password: string, email: string): string {
  if (hasEmoji(password)) {
    return "No uses emojis: pueden escribirse distinto en otro teclado o dispositivo y la persona no podría entrar.";
  }
  if (password.length < MIN_PASSWORD_LENGTH) return `Usa al menos ${MIN_PASSWORD_LENGTH} caracteres.`;
  if (new TextEncoder().encode(password).length > MAX_PASSWORD_BYTES) {
    return `Es demasiado larga: el máximo son ${MAX_PASSWORD_BYTES} caracteres comunes (los acentos, símbolos y emojis ocupan más).`;
  }
  if (password.trim().toLowerCase() === email.trim().toLowerCase()) return "No puede ser igual al correo.";
  return "";
}

/** Message to show when the API rejects the new password. */
export function resetPasswordMessage(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.status === 400) return e.detail ? `El servidor no aceptó la contraseña: ${e.detail}` : "El servidor no aceptó la contraseña. Revisa la política.";
    if (e.status === 403) return "Tu rol no puede definir contraseñas. Solo un administrador puede hacerlo.";
    if (e.status === 404) return "Este usuario ya no existe. Actualiza la lista.";
    if (e.status === 0) return "No pudimos conectar con el servidor. Revisa tu conexión e intenta de nuevo.";
  }
  return "No pudimos definir la contraseña. Intenta de nuevo.";
}

export function resetPassword(token: string, userId: string, newPassword: string) {
  return apiFetch<void>(`/api/console/users/${userId}/password`, { token, method: "POST", body: { newPassword } });
}

/** Changes the password of the signed-in person (POST /auth/change-password). */
export function changePassword(token: string, currentPassword: string, newPassword: string) {
  return apiFetch<void>("/api/console/auth/change-password", {
    token,
    method: "POST",
    body: { currentPassword, newPassword },
  });
}

/** Message to show when the API rejects the password change. */
export function changePasswordMessage(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.status === 400) {
      return e.detail
        ? `El servidor no aceptó el cambio: ${e.detail}`
        : "La contraseña actual no es correcta o la nueva no cumple la política. Revisa los dos campos.";
    }
    if (e.status === 401) return "Tu sesión terminó. Inicia sesión de nuevo.";
    if (e.status === 0) return "No pudimos conectar con el servidor. Revisa tu conexión e intenta de nuevo.";
  }
  return "No pudimos cambiar la contraseña. Intenta de nuevo.";
}
