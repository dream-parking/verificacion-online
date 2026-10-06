"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
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

function SelectorRol({ id, oscuro = false }: { id: string; oscuro?: boolean }) {
  const { rol, setRol } = useConsola();
  return (
    <div className="min-w-[190px]">
      <label htmlFor={id} className={`lbl ${oscuro ? "text-line-mid" : ""}`}>
        Rol de demostración
      </label>
      <select
        id={id}
        className={`fld h-9 ${oscuro ? "border-white text-white [&>option]:text-ink" : ""}`}
        value={rol}
        onChange={(e) => setRol(e.target.value as Rol)}
      >
        <option value="gerardo">Gerardo · Conozca a su Cliente</option>
        <option value="ana">Ana · Fraude y cumplimiento</option>
      </select>
    </div>
  );
}

export function Shell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const { usuario, alertas } = useConsola();
  const [menuAbierto, setMenuAbierto] = useState(false);
  const sinAsignar = alertas.filter((x) => x.estado === "Sin asignar").length;

  // En pantallas pequeñas el menú lateral se cierra con Escape.
  useEffect(() => {
    if (!menuAbierto) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setMenuAbierto(false);
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [menuAbierto]);

  const nav = [
    { href: "/solicitudes", label: "Solicitudes", badge: 0 },
    { href: "/alertas", label: "Bandeja de alertas", badge: sinAsignar },
    { href: "/regla", label: "Regla de score", badge: 0 },
  ];

  return (
    <div className="min-h-screen bg-surface lg:flex">
      {/* Fondo oscuro del menú en pantallas pequeñas */}
      {menuAbierto && (
        <div className="anim-aparecer fixed inset-0 z-40 bg-black/40 lg:hidden" aria-hidden="true" onClick={() => setMenuAbierto(false)} />
      )}

      {/* Barra lateral: fija en escritorio, desplegable en móvil y tableta */}
      <nav
        id="menu-principal"
        aria-label="Navegación principal"
        className={`fixed inset-y-0 left-0 z-50 flex w-[248px] flex-col overflow-y-auto bg-dark text-white transition-[transform,visibility] duration-300 ease-out-soft lg:sticky lg:top-0 lg:z-auto lg:h-screen lg:flex-none lg:translate-x-0 lg:visible ${
          menuAbierto ? "visible translate-x-0" : "invisible -translate-x-full"
        }`}
      >
        <div className="flex items-center justify-between px-5 pt-[22px] pb-6">
          <Logo />
          <button
            type="button"
            className="flex size-11 items-center justify-center rounded-full text-white hover:bg-dark-hover lg:hidden"
            aria-label="Cerrar menú"
            onClick={() => setMenuAbierto(false)}
          >
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </div>
        <div className="flex flex-col gap-0.5">
          {nav.map((n) => (
            <Link
              key={n.href}
              href={n.href}
              className="nav-item"
              aria-current={pathname.startsWith(n.href) ? "page" : undefined}
              onClick={() => setMenuAbierto(false)}
            >
              <span>{n.label}</span>
              {n.badge > 0 && (
                <span key={n.badge} className="nav-badge anim-rebote" aria-label={`${n.badge} alertas sin asignar`}>
                  {n.badge}
                </span>
              )}
            </Link>
          ))}
        </div>
        <div className="flex-1" />
        <div className="border-t border-dark-line p-5 md:hidden">
          <SelectorRol id="rol-menu" oscuro />
        </div>
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
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex min-h-16 items-center justify-between gap-x-6 gap-y-3 border-b border-line bg-white px-4 py-3 md:min-h-[72px] md:px-8">
          <div className="flex min-w-0 items-center gap-2">
            <button
              type="button"
              className="-ml-2 flex size-11 flex-none items-center justify-center rounded-full hover:bg-soft lg:hidden"
              aria-label="Abrir menú"
              aria-expanded={menuAbierto}
              aria-controls="menu-principal"
              onClick={() => setMenuAbierto(true)}
            >
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
                <path d="M4 7h16M4 12h16M4 17h16" />
              </svg>
            </button>
            <div className="truncate font-display text-lg font-extrabold md:text-xl">{tituloDe(pathname)}</div>
          </div>
          <div className="flex flex-none items-center gap-4">
            <div className="hidden md:block">
              <SelectorRol id="rol" />
            </div>
            <div className="flex items-center gap-2.5">
              <div
                className="flex size-10 flex-none items-center justify-center rounded-full bg-ink font-display text-[15px] font-extrabold text-white"
                title={`${usuario.nombre} · ${usuario.cargo}`}
                aria-hidden="true"
              >
                {usuario.ini}
              </div>
              <div className="hidden sm:max-md:block xl:block">
                <div className="text-[15px] leading-5 font-bold">{usuario.nombre}</div>
                <div className="text-[13px] leading-[18px] text-muted">{usuario.cargo}</div>
              </div>
              <span className="sr-only sm:max-md:hidden xl:hidden">{usuario.nombre}</span>
            </div>
          </div>
        </header>

        <main className="flex min-w-0 flex-col px-4 pt-5 pb-10 md:px-8 md:pt-7">
          {/* La key reinicia la animación de entrada en cada cambio de ruta. */}
          <div key={pathname} className="anim-pagina flex min-w-0 flex-col gap-5">
            {children}
          </div>
        </main>
      </div>
    </div>
  );
}
