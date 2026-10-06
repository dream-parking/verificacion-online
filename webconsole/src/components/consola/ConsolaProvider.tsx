"use client";

import { createContext, useContext, useState, type ReactNode } from "react";
import { ALERTAS, USUARIOS, type Alerta, type Rol, type Usuario } from "@/lib/consola/datos";

type ConsolaContext = {
  rol: Rol;
  setRol: (rol: Rol) => void;
  usuario: Usuario;
  alertas: Alerta[];
  /** Asigna la alerta al usuario actual y devuelve el mensaje de confirmación. */
  tomarAlerta: (id: string) => string;
};

const Ctx = createContext<ConsolaContext | null>(null);

export function ConsolaProvider({ children }: { children: ReactNode }) {
  const [rol, setRol] = useState<Rol>("gerardo");
  const [alertas, setAlertas] = useState<Alerta[]>(ALERTAS);
  const usuario = USUARIOS[rol];

  const tomarAlerta = (id: string) => {
    const al = alertas.find((x) => x.id === id);
    setAlertas((prev) =>
      prev.map((x) => (x.id === id ? { ...x, estado: "Asignada", resp: usuario.nombre } : x)),
    );
    return `Alerta de la cuenta ${al?.cuenta ?? ""} asignada a ${usuario.nombre}.`;
  };

  return <Ctx.Provider value={{ rol, setRol, usuario, alertas, tomarAlerta }}>{children}</Ctx.Provider>;
}

export function useConsola() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useConsola debe usarse dentro de <ConsolaProvider>");
  return ctx;
}
