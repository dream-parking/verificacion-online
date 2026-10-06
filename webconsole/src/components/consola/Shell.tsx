"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import type { Rol } from "@/lib/consola/datos";
import { useConsola } from "./ConsolaProvider";
import { Logo } from "./Logo";

const ONBOARDING_URL = process.env.NEXT_PUBLIC_ONBOARDING_URL;

function tituloDe(pathname: string) {
  if (pathname.startsWith("/solicitudes/")) return "Solicitudes · Detalle";
  if (pathname.startsWith("/solicitudes")) return "Solicitudes";
  if (pathname.startsWith("/alertas")) return "Bandeja de alertas";
  if (pathname.startsWith("/regla")) return "Regla de score";
  return "";
}

export function Shell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const { rol, setRol, usuario, alertas } = useConsola();
  const sinAsignar = alertas.filter((x) => x.estado === "Sin asignar").length;

  const nav = [
    { href: "/solicitudes", label: "Solicitudes", badge: 0 },
    { href: "/alertas", label: "Bandeja de alertas", badge: sinAsignar },
    { href: "/regla", label: "Regla de score", badge: 0 },
  ];

  return (
    <div className="flex min-h-screen flex-wrap bg-surface">
      {/* Barra lateral */}
      <nav aria-label="Navegación principal" className="flex min-h-[420px] flex-[0_0_248px] flex-col bg-dark text-white">
        <div className="px-5 pt-[22px] pb-6">
          <Logo />
        </div>
        <div className="flex flex-col gap-0.5">
          {nav.map((n) => (
            <Link
              key={n.href}
              href={n.href}
              className="nav-item"
              aria-current={pathname.startsWith(n.href) ? "page" : undefined}
            >
              <span>{n.label}</span>
              {n.badge > 0 && (
                <span className="nav-badge" aria-label={`${n.badge} alertas sin asignar`}>
                  {n.badge}
                </span>
              )}
            </Link>
          ))}
        </div>
        <div className="flex-1" />
        {ONBOARDING_URL && (
          <div className="flex flex-col gap-2.5 border-t border-dark-line p-5">
            <span className="text-[13px] text-line-mid">Atajo de la demostración</span>
            <a
              href={ONBOARDING_URL}
              className="inline-flex min-h-11 items-center justify-center rounded-full border border-white px-4 font-display text-sm font-bold uppercase text-white no-underline"
            >
              Ver app móvil
            </a>
          </div>
        )}
      </nav>

      {/* Columna principal */}
      <div className="flex min-w-0 flex-[999_1_560px] flex-col">
        <header className="flex min-h-[72px] flex-wrap items-center justify-between gap-x-6 gap-y-3 border-b border-line bg-white px-8 py-3">
          <div className="font-display text-xl font-extrabold">{tituloDe(pathname)}</div>
          <div className="flex flex-wrap items-center gap-4">
            <div className="min-w-[190px]">
              <label htmlFor="rol" className="lbl">
                Rol de demostración
              </label>
              <select id="rol" className="fld h-9" value={rol} onChange={(e) => setRol(e.target.value as Rol)}>
                <option value="gerardo">Gerardo · Conozca a su Cliente</option>
                <option value="ana">Ana · Fraude y cumplimiento</option>
              </select>
            </div>
            <div className="flex items-center gap-2.5">
              <div
                className="flex size-10 items-center justify-center rounded-full bg-ink font-display text-[15px] font-extrabold text-white"
                aria-hidden="true"
              >
                {usuario.ini}
              </div>
              <div>
                <div className="text-[15px] leading-5 font-bold">{usuario.nombre}</div>
                <div className="text-[13px] leading-[18px] text-muted">{usuario.cargo}</div>
              </div>
            </div>
          </div>
        </header>

        <main className="flex min-w-0 flex-col gap-5 px-8 pt-7 pb-10">{children}</main>
      </div>
    </div>
  );
}
