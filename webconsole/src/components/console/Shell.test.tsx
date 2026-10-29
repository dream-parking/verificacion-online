import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ComponentProps } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Alert, ConsoleUser } from "@/lib/console/api";
import { signOut, startSession } from "@/lib/console/session";

let pathname = "/requests";
vi.mock("next/navigation", () => ({ usePathname: () => pathname }));
vi.mock("next/image", () => ({
  // eslint-disable-next-line @next/next/no-img-element
  default: ({ alt, src }: { alt: string; src: string }) => <img alt={alt} src={src} />,
}));

vi.mock("next/link", () => ({
  // jsdom no navega: se evita el aviso "navigation not implemented" al hacer clic en un enlace.
  default: ({ href, children, onClick, ...rest }: ComponentProps<"a">) => (
    <a
      href={href}
      {...rest}
      onClick={(e) => {
        e.preventDefault();
        onClick?.(e);
      }}
    >
      {children}
    </a>
  ),
}));

let alertsData: Alert[] = [];
let currentUser: ConsoleUser;
vi.mock("./ConsoleProvider", () => ({
  useConsole: () => ({ user: currentUser, token: "tok", alerts: { data: alertsData }, takeAlert: vi.fn() }),
}));

import { Shell } from "./Shell";

const makeUser = (over: Partial<ConsoleUser> = {}): ConsoleUser => ({
  id: "u1",
  email: "ana@example.com",
  fullName: "Ana María Pérez",
  initials: null,
  jobTitle: null,
  role: "KYC_LEAD",
  active: true,
  ...over,
});

const alert = (id: string, status: Alert["status"]): Alert => ({
  id,
  account: "1",
  reason: "r",
  criticality: "LOW",
  status,
  assigneeId: null,
  assigneeName: null,
  raisedAt: "2026-10-06T04:08:03Z",
});

beforeEach(() => {
  pathname = "/requests";
  alertsData = [];
  currentUser = makeUser();
});

describe("Shell", () => {
  it("muestra el contenido sin repetir el título de la página en la barra", () => {
    render(<Shell>página</Shell>);
    expect(screen.getByText("página")).toBeInTheDocument();
    expect(screen.queryByText("Solicitudes", { selector: "div.truncate" })).not.toBeInTheDocument();
  });

  it.each([
    ["/requests/abc", "Solicitudes · Detalle"],
    ["/requests", ""],
    ["/alerts", ""],
    ["/map", ""],
    ["/score-model", ""],
    ["/users", ""],
    ["/otra", ""],
  ])("el título de %s es «%s»", (path, title) => {
    pathname = path;
    const { container } = render(<Shell>x</Shell>);
    expect(container.querySelector("header .truncate")).toHaveTextContent(title);
  });

  it("marca la sección actual en el menú", () => {
    pathname = "/alerts";
    render(<Shell>x</Shell>);
    const menu = screen.getByRole("navigation", { name: "Navegación principal", hidden: true });
    expect(within(menu).getByRole("link", { name: /Bandeja de alertas/, hidden: true })).toHaveAttribute("aria-current", "page");
    expect(within(menu).getByRole("link", { name: /Solicitudes/, hidden: true })).not.toHaveAttribute("aria-current");
  });

  it("solo el administrador ve Usuarios", () => {
    const { unmount } = render(<Shell>x</Shell>);
    expect(screen.queryByRole("link", { name: "Usuarios", hidden: true })).not.toBeInTheDocument();
    unmount();

    currentUser = makeUser({ role: "ADMIN" });
    render(<Shell>x</Shell>);
    expect(screen.getByRole("link", { name: "Usuarios", hidden: true })).toBeInTheDocument();
  });

  it("Expedientes lo ven todos los roles y Tolerancia del perfil solo el administrador", () => {
    for (const role of ["KYC_LEAD", "FRAUD_ANALYST"] as const) {
      currentUser = makeUser({ role });
      const { unmount } = render(<Shell>x</Shell>);
      expect(screen.getByRole("link", { name: "Expedientes", hidden: true })).toHaveAttribute("href", "/dossiers");
      expect(screen.queryByRole("link", { name: "Tolerancia del perfil", hidden: true })).not.toBeInTheDocument();
      unmount();
    }

    currentUser = makeUser({ role: "ADMIN" });
    render(<Shell>x</Shell>);
    expect(screen.getByRole("link", { name: "Expedientes", hidden: true })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Tolerancia del perfil", hidden: true })).toHaveAttribute(
      "href",
      "/profile-tolerance",
    );
  });

  it("cuenta las alertas sin asignar en el menú", () => {
    alertsData = [alert("1", "UNASSIGNED"), alert("2", "UNASSIGNED"), alert("3", "CLOSED")];
    render(<Shell>x</Shell>);
    expect(screen.getByLabelText("2 alertas sin asignar", { selector: "span" })).toHaveTextContent("2");
  });

  it("sin alertas sin asignar no muestra contador", () => {
    alertsData = [alert("1", "ASSIGNED")];
    render(<Shell>x</Shell>);
    expect(screen.queryByLabelText(/alertas sin asignar/)).not.toBeInTheDocument();
  });

  describe("persona de la sesión", () => {
    it("usa las iniciales del perfil si existen", () => {
      currentUser = makeUser({ initials: "XY" });
      render(<Shell>x</Shell>);
      expect(screen.getByText("XY")).toBeInTheDocument();
    });

    it("si no hay, las saca de las dos primeras palabras del nombre", () => {
      render(<Shell>x</Shell>);
      expect(screen.getByText("AM")).toBeInTheDocument();
    });

    it("muestra el cargo, o la etiqueta del rol si no tiene", () => {
      const { unmount } = render(<Shell>x</Shell>);
      expect(screen.getByText("Conozca a su Cliente")).toBeInTheDocument();
      unmount();

      currentUser = makeUser({ jobTitle: "Jefa de KYC" });
      render(<Shell>x</Shell>);
      expect(screen.getByText("Jefa de KYC")).toBeInTheDocument();
    });
  });

  it("Cerrar sesión termina la sesión", async () => {
    act(() => startSession({ accessToken: "tok", expiresIn: 3600, tokenType: "Bearer", user: currentUser }));
    render(<Shell>x</Shell>);
    await userEvent.click(screen.getByRole("button", { name: "Cerrar sesión" }));
    expect(sessionStorage.getItem("console.session")).toBeNull();
    act(() => signOut());
  });

  it("Cambiar contraseña abre y cierra el diálogo", async () => {
    render(<Shell>x</Shell>);
    await userEvent.click(screen.getByRole("button", { name: "Cambiar contraseña" }));
    expect(screen.getByRole("dialog", { name: "Cambiar mi contraseña" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Cancelar" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  describe("menú lateral", () => {
    it("el botón de escritorio lo oculta y lo vuelve a mostrar", async () => {
      render(<Shell>x</Shell>);
      // Hay dos botones para ocultarlo (en la barra lateral y en la cabecera); el de la cabecera lo alterna.
      const [, headerToggle] = screen.getAllByRole("button", { name: "Ocultar menú lateral", hidden: true });
      await userEvent.click(headerToggle);
      expect(screen.getByRole("button", { name: "Mostrar menú lateral", hidden: true })).toHaveAttribute("aria-expanded", "false");

      await userEvent.click(screen.getByRole("button", { name: "Mostrar menú lateral", hidden: true }));
      expect(screen.getAllByRole("button", { name: "Ocultar menú lateral", hidden: true })).toHaveLength(2);
    });

    it("en móvil se abre con el botón y se cierra con Escape", async () => {
      render(<Shell>x</Shell>);
      const open = screen.getByRole("button", { name: "Abrir menú", hidden: true });
      expect(open).toHaveAttribute("aria-expanded", "false");

      await userEvent.click(open);
      expect(open).toHaveAttribute("aria-expanded", "true");

      await userEvent.keyboard("{Escape}");
      expect(open).toHaveAttribute("aria-expanded", "false");
    });

    it("en móvil se cierra al elegir una opción o con el botón de cerrar", async () => {
      render(<Shell>x</Shell>);
      const open = screen.getByRole("button", { name: "Abrir menú", hidden: true });

      await userEvent.click(open);
      await userEvent.click(screen.getByRole("link", { name: /Mapa de ubicaciones/, hidden: true }));
      expect(open).toHaveAttribute("aria-expanded", "false");

      await userEvent.click(open);
      await userEvent.click(screen.getByRole("button", { name: "Cerrar menú", hidden: true }));
      expect(open).toHaveAttribute("aria-expanded", "false");
    });
  });
});
