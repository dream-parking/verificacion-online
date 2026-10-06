"use client";

import { useRouter } from "next/navigation";
import { createContext, useContext, useEffect, type ReactNode } from "react";
import { apiFetch, ApiError, type Alerta, type UsuarioConsola } from "@/lib/consola/api";
import { cerrarSesion, useSesion } from "@/lib/consola/sesion";
import { useCarga } from "./ui";

type ConsolaContext = {
  usuario: UsuarioConsola;
  token: string;
  /** Alertas abiertas: las usan la bandeja y el contador del menú lateral. */
  alertas: ReturnType<typeof useCarga<Alerta[]>>;
  /** Se asigna la alerta al usuario de la sesión. Devuelve la alerta actualizada. */
  tomarAlerta: (id: string) => Promise<Alerta>;
};

const Ctx = createContext<ConsolaContext | null>(null);

/** Solo se monta con una sesión válida (ver Shell). */
export function ConsolaProvider({
  usuario,
  token,
  children,
}: {
  usuario: UsuarioConsola;
  token: string;
  children: ReactNode;
}) {
  const alertas = useCarga("alertas", (t) => apiFetch<Alerta[]>("/api/console/alerts", { token: t }));

  const tomarAlerta = async (id: string) => {
    try {
      const alerta = await apiFetch<Alerta>(`/api/console/alerts/${id}/take`, { token, method: "POST" });
      alertas.actualizar((lista) => lista.map((x) => (x.id === id ? alerta : x)));
      return alerta;
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) cerrarSesion();
      // Otra persona la tomó o cambió de estado: refrescar para mostrar el estado real.
      if (e instanceof ApiError && e.status === 409) alertas.recargar();
      throw e;
    }
  };

  return <Ctx.Provider value={{ usuario, token, alertas, tomarAlerta }}>{children}</Ctx.Provider>;
}

export function useConsola() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useConsola debe usarse dentro de <ConsolaProvider>");
  return ctx;
}

/**
 * Exige una sesión: sin ella manda al login. Mientras se lee la sesión (primer
 * render en el servidor) no muestra nada para no enseñar la consola a quien no entró.
 */
export function ConSesion({ children }: { children: ReactNode }) {
  const sesion = useSesion();
  const router = useRouter();

  useEffect(() => {
    if (sesion === null) router.replace("/login");
  }, [sesion, router]);

  if (!sesion) return null;
  return (
    <ConsolaProvider usuario={sesion.user} token={sesion.accessToken}>
      {children}
    </ConsolaProvider>
  );
}
