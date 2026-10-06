"use client";

import { useSyncExternalStore } from "react";
import type { LoginResponse, UsuarioConsola } from "./api";

// La sesión vive en localStorage para compartirse entre pestañas; caduca con el token (8 h).

export type Sesion = { accessToken: string; expiresAt: number; user: UsuarioConsola };

const CLAVE = "consola.sesion";
const oyentes = new Set<() => void>();
let cache: Sesion | null | undefined;

function leer(): Sesion | null {
  if (cache === undefined) {
    try {
      const s = JSON.parse(localStorage.getItem(CLAVE) ?? "null") as Sesion | null;
      cache = s && s.expiresAt > Date.now() ? s : null;
    } catch {
      cache = null;
    }
  }
  return cache;
}

function avisar() {
  oyentes.forEach((o) => o());
}

function suscribir(oyente: () => void) {
  oyentes.add(oyente);
  // Otra pestaña inició o cerró sesión.
  const alCambiar = (e: StorageEvent) => {
    if (e.key === CLAVE) {
      cache = undefined;
      oyente();
    }
  };
  window.addEventListener("storage", alCambiar);
  return () => {
    oyentes.delete(oyente);
    window.removeEventListener("storage", alCambiar);
  };
}

export function iniciarSesion(r: LoginResponse) {
  cache = { accessToken: r.accessToken, expiresAt: Date.now() + r.expiresIn * 1000, user: r.user };
  try {
    localStorage.setItem(CLAVE, JSON.stringify(cache));
  } catch {
    // Sin almacenamiento la sesión dura lo que dure la pestaña.
  }
  avisar();
}

export function cerrarSesion() {
  cache = null;
  try {
    localStorage.removeItem(CLAVE);
  } catch {}
  avisar();
}

/** null: sin sesión. undefined: todavía no se sabe (render en el servidor). */
export function useSesion(): Sesion | null | undefined {
  return useSyncExternalStore(suscribir, leer, () => undefined);
}
