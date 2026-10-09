"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { ROLE_LABEL } from "@/lib/console/format";
import { signOut } from "@/lib/console/session";
import { useConsole } from "./ConsoleProvider";
import { Logo } from "./Logo";

const ONBOARDING_URL = process.env.NEXT_PUBLIC_ONBOARDING_URL;

function titleFor(pathname: string) {
  if (pathname.startsWith("/requests/")) return "Solicitudes · Detalle";
  if (pathname.startsWith("/requests")) return "Solicitudes";
  if (pathname.startsWith("/alerts")) return "Bandeja de alertas";
  if (pathname.startsWith("/map")) return "Mapa de ubicaciones";
  if (pathname.startsWith("/score-rule")) return "Regla de score";
  if (pathname.startsWith("/users")) return "Usuarios";
  return "";
}

export function Shell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const { user, alerts } = useConsole();
  // menuOpen: dropdown menu on mobile and tablet. collapsed: sidebar hidden on desktop.
  const [menuOpen, setMenuOpen] = useState(false);
  const [collapsed, setCollapsed] = useState(false);
  const unassigned = (alerts.data ?? []).filter((x) => x.status === "UNASSIGNED").length;
  const initials =
    user.initials ||
    user.fullName
      .split(/s+/)
      .slice(0, 2)
      .map((p) => p[0])
      .join("")
      .toUpperCase();
  const jobTitle = user.jobTitle || ROLE_LABEL[user.role];

  // On small screens the side menu closes with Escape.
  useEffect(() => {
    if (!menuOpen) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setMenuOpen(false);
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [menuOpen]);

  const nav = [
    { href: "/requests", label: "Solicitudes", badge: 0 },
    { href: "/alerts", label: "Bandeja de alertas", badge: unassigned },
    { href: "/map", label: "Mapa de ubicaciones", badge: 0 },
    { href: "/score-rule", label: "Regla de score", badge: 0 },
    // Only an administrator manages users.
    ...(user.role === "ADMIN" ? [{ href: "/users", label: "Usuarios", badge: 0 }] : []),
  ];

  return (
    <div className="min-h-screen bg-surface lg:flex">
      {/* Dark backdrop of the menu on small screens */}
      {menuOpen && (
        <div className="anim-fade fixed inset-0 z-40 bg-black/40 lg:hidden" aria-hidden="true" onClick={() => setMenuOpen(false)} />
      )}

      {/* Sidebar: fixed on desktop (can be hidden), dropdown on mobile and tablet.
          Its inner content keeps its width so it does not reflow while animating. */}
      <nav
        id="menu-principal"
        aria-label="Navegación principal"
        className={`fixed inset-y-0 left-0 z-50 w-[248px] overflow-hidden bg-dark text-white transition-[transform,visibility,width] duration-300 ease-out-soft lg:sticky lg:top-0 lg:z-auto lg:h-screen lg:flex-none lg:translate-x-0 ${
          menuOpen ? "visible translate-x-0" : "invisible -translate-x-full"
        } ${collapsed ? "lg:invisible lg:w-0" : "lg:visible"}`}
      >
        <div className="flex h-full w-[248px] flex-col overflow-y-auto">
          <div className="flex items-center justify-between gap-2 py-5 pr-3 pl-4">
            <Logo />
            <button
              type="button"
              className="flex size-11 items-center justify-center rounded-full text-white hover:bg-dark-hover lg:hidden"
              aria-label="Cerrar menú"
              onClick={() => setMenuOpen(false)}
            >
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
                <path d="M6 6l12 12M18 6L6 18" />
              </svg>
            </button>
            <button
              type="button"
              className="hidden size-9 flex-none items-center justify-center rounded-full text-white hover:bg-dark-hover lg:flex"
              aria-label="Ocultar menú lateral"
              aria-controls="menu-principal"
              aria-expanded={!collapsed}
              onClick={() => setCollapsed(true)}
            >
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M15 6l-6 6 6 6" />
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
                onClick={() => setMenuOpen(false)}
              >
                <span>{n.label}</span>
                {n.badge > 0 && (
                  <span key={n.badge} className="nav-badge anim-bounce" aria-label={`${n.badge} alertas sin asignar`}>
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
        </div>
      </nav>

      {/* Main column */}
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex min-h-16 items-center justify-between gap-x-6 gap-y-3 border-b border-line bg-white px-4 py-3 md:min-h-[72px] md:px-8">
          <div className="flex min-w-0 items-center gap-2">
            <button
              type="button"
              className="-ml-2 flex size-11 flex-none items-center justify-center rounded-full hover:bg-soft lg:hidden"
              aria-label="Abrir menú"
              aria-expanded={menuOpen}
              aria-controls="menu-principal"
              onClick={() => setMenuOpen(true)}
            >
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
                <path d="M4 7h16M4 12h16M4 17h16" />
              </svg>
            </button>
            <button
              type="button"
              className="-ml-2 hidden size-11 flex-none items-center justify-center rounded-full hover:bg-soft lg:flex"
              aria-label={collapsed ? "Mostrar menú lateral" : "Ocultar menú lateral"}
              aria-expanded={!collapsed}
              aria-controls="menu-principal"
              onClick={() => setCollapsed((c) => !c)}
            >
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
                <path d="M4 7h16M4 12h16M4 17h16" />
              </svg>
            </button>
            <div className="truncate font-display text-lg font-extrabold md:text-xl">{titleFor(pathname)}</div>
          </div>
          <div className="flex flex-none items-center gap-3 md:gap-4">
            <div className="flex items-center gap-2.5">
              <div
                className="flex size-10 flex-none items-center justify-center rounded-full bg-ink font-display text-[15px] font-extrabold text-white"
                title={`${user.fullName} · ${jobTitle}`}
                aria-hidden="true"
              >
                {initials}
              </div>
              <div className="hidden sm:block">
                <div className="text-[15px] leading-5 font-bold">{user.fullName}</div>
                <div className="text-[13px] leading-[18px] text-muted">{jobTitle}</div>
              </div>
              <span className="sr-only sm:hidden">{user.fullName}</span>
            </div>
            <button type="button" className="btn2 min-h-10 gap-2 px-3 text-[13px] md:px-4" onClick={signOut}>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 17l-5-5 5-5M5 12h11" />
              </svg>
              <span className="max-md:sr-only">Cerrar sesión</span>
            </button>
          </div>
        </header>

        <main className="flex min-w-0 flex-col px-4 pt-5 pb-10 md:px-8 md:pt-7">
          {/* The key restarts the entrance animation on every route change. */}
          <div key={pathname} className="anim-page flex min-w-0 flex-col gap-5">
            {children}
          </div>
        </main>
      </div>
    </div>
  );
}
