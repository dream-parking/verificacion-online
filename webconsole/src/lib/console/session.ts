"use client";

import { useSyncExternalStore } from "react";
import type { LoginResponse, ConsoleUser } from "./api";

// The session lives in sessionStorage: the browser discards it when the tab is closed, and each tab
// has its own session. It also expires with the token (8 h). Reloading the page keeps it.

export type Session = { accessToken: string; expiresAt: number; user: ConsoleUser };

const STORAGE_KEY = "console.session";
// Earlier versions kept the session in localStorage, which survived closing the tab.
const LEGACY_STORAGE_KEY = "consola.sesion";
const listeners = new Set<() => void>();
let cache: Session | null | undefined;

function removeLegacySession() {
  try {
    localStorage.removeItem(LEGACY_STORAGE_KEY);
  } catch {
    // Storage may be blocked; there is nothing to clean up then.
  }
}

function read(): Session | null {
  if (cache === undefined) {
    removeLegacySession();
    try {
      const stored = JSON.parse(sessionStorage.getItem(STORAGE_KEY) ?? "null") as Session | null;
      cache = stored && stored.expiresAt > Date.now() ? stored : null;
    } catch {
      cache = null;
    }
  }
  return cache;
}

function notify() {
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function startSession(response: LoginResponse) {
  cache = { accessToken: response.accessToken, expiresAt: Date.now() + response.expiresIn * 1000, user: response.user };
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(cache));
  } catch {
    // Without storage the session lasts only until the page is reloaded.
  }
  notify();
}

export function signOut() {
  cache = null;
  try {
    sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // Nothing stored, nothing to remove.
  }
  removeLegacySession();
  notify();
}

/** null: no session. undefined: not known yet (server render). */
export function useSession(): Session | null | undefined {
  return useSyncExternalStore(subscribe, read, () => undefined);
}
