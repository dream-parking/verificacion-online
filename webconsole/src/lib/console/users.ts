// Console user management (ADMIN only). Contract: docs/openapi.json, POST /users/{id}/password.

import { ApiError, apiFetch } from "./api";

export const MIN_PASSWORD_LENGTH = 10;
/** The API limits the password to 72 bytes (bcrypt limit): accents and emojis take more than one. */
export const MAX_PASSWORD_BYTES = 72;

export const PASSWORD_POLICY = `De ${MIN_PASSWORD_LENGTH} a ${MAX_PASSWORD_BYTES} caracteres y distinta del correo.`;

/** Message of the first rule the password breaks, or "" if it is valid. The API validates it again. */
export function passwordError(password: string, email: string): string {
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
