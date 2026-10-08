"use client";

import { useEffect, useState } from "react";
import { ApiError } from "./api";
import { signOut, useSession } from "./session";

type LoadResult<T> = { key: string; data?: T; error?: ApiError };

/**
 * Loads API data with the session token. `key` identifies the load: when it changes the data is
 * requested again. A 401 ends the session (expired token or deactivated user) and the console
 * goes back to the login.
 */
export function useLoad<T>(key: string | null, loader: (token: string) => Promise<T>) {
  const token = useSession()?.accessToken ?? null;
  const [attempt, setAttempt] = useState(0);
  const [res, setRes] = useState<LoadResult<T>>();
  const id = key && token ? `${key}#${attempt}` : null;

  useEffect(() => {
    if (!id || !token) return;
    let isCurrent = true;
    loader(token).then(
      (data) => isCurrent && setRes({ key: id, data }),
      (e: unknown) => {
        if (!isCurrent) return;
        const error = e instanceof ApiError ? e : new ApiError(0, "Error", String(e));
        if (error.status === 401) signOut();
        setRes({ key: id, error });
      },
    );
    return () => {
      isCurrent = false;
    };
    // `id` already reflects everything that defines the load.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const current = res?.key === id ? res : undefined;
  return {
    data: current?.data,
    error: current?.error,
    loading: !!id && !current,
    reload: () => setAttempt((i) => i + 1),
    /** Updates the data already loaded without requesting it again (for example, after taking an alert). */
    update: (fn: (d: T) => T) =>
      setRes((r) => (r && r.key === id && r.data !== undefined ? { ...r, data: fn(r.data) } : r)),
  };
}

/** Message to show the person for an API error. */
export function errorMessage(e: ApiError | undefined, fallback: string) {
  if (!e) return fallback;
  if (e.status === 0) return "Hubo un problema de conexión con el servidor. Revisa tu conexión e intenta de nuevo.";
  if (e.status === 403) return "Tu rol no tiene permiso para ver esta información.";
  if (e.status === 404) return "No encontramos lo que buscas. Puede que ya no exista.";
  return fallback;
}

export const PAGE_SIZES = [5, 10, 20];

/**
 * Filter and pagination state of a table. Changing any filter or the page size goes back to the
 * first page.
 */
export function usePaginatedFilters<F extends Record<string, string>>(initial: F, initialSize = PAGE_SIZES[0]) {
  const [filters, setFilters] = useState<F>(initial);
  const [page, setPage] = useState(1);
  const [size, setSizeState] = useState(initialSize);

  const setFilter = (k: keyof F, v: string) => {
    setFilters((f) => ({ ...f, [k]: v }));
    setPage(1);
  };
  const clear = () => {
    setFilters(initial);
    setPage(1);
  };
  const setSize = (n: number) => {
    setSizeState(n);
    setPage(1);
  };
  const hasFilters = (Object.keys(initial) as (keyof F)[]).some((k) => filters[k] !== initial[k]);

  function paginate<T>(items: T[]) {
    const totalPages = Math.max(1, Math.ceil(items.length / size));
    const current = Math.min(page, totalPages);
    return {
      visibleItems: items.slice((current - 1) * size, current * size),
      page: current,
      totalPages,
      total: items.length,
    };
  }

  return { filters, setFilter, clear, hasFilters, size, setSize, setPage, paginate };
}
