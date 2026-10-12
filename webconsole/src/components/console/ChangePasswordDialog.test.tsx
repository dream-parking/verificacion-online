import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ChangePasswordDialog } from "./ChangePasswordDialog";

const EMAIL = "ana@example.com";
const VALID = "una-clave-larga-1";

function setup() {
  const onClose = vi.fn();
  render(<ChangePasswordDialog email={EMAIL} token="tok" onClose={onClose} />);
  return { onClose };
}

const fill = async (current: string, next: string, repeat: string) => {
  await userEvent.type(screen.getByLabelText("Contraseña actual"), current);
  await userEvent.type(screen.getByLabelText("Contraseña nueva"), next);
  await userEvent.type(screen.getByLabelText("Repite la contraseña nueva"), repeat);
};

const submit = () => userEvent.click(screen.getByRole("button", { name: "Cambiar contraseña" }));

afterEach(() => vi.unstubAllGlobals());

describe("ChangePasswordDialog", () => {
  it("abre con el foco en la contraseña actual", () => {
    setup();
    expect(screen.getByRole("dialog", { name: "Cambiar mi contraseña" })).toBeInTheDocument();
    expect(screen.getByLabelText("Contraseña actual")).toHaveFocus();
  });

  it("Escape y Cancelar cierran el diálogo", async () => {
    const { onClose } = setup();
    await userEvent.keyboard("{Escape}");
    await userEvent.click(screen.getByRole("button", { name: "Cancelar" }));
    expect(onClose).toHaveBeenCalledTimes(2);
  });

  it("Mostrar/Ocultar alterna la visibilidad de los tres campos", async () => {
    setup();
    expect(screen.getByLabelText("Contraseña actual")).toHaveAttribute("type", "password");
    await userEvent.click(screen.getByRole("button", { name: "Mostrar" }));
    expect(screen.getByLabelText("Contraseña actual")).toHaveAttribute("type", "text");
    expect(screen.getByLabelText("Repite la contraseña nueva")).toHaveAttribute("type", "text");
    expect(screen.getByRole("button", { name: "Ocultar" })).toHaveAttribute("aria-pressed", "true");
  });

  it("sin contraseña actual lo pide y no llama a la API", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    setup();
    await userEvent.type(screen.getByLabelText("Contraseña nueva"), VALID);
    await userEvent.type(screen.getByLabelText("Repite la contraseña nueva"), VALID);
    await submit();
    expect(screen.getByText("Escribe tu contraseña actual.")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("aplica la política a la contraseña nueva", async () => {
    setup();
    await fill("actual-clave-1", "corta", "corta");
    await submit();
    expect(screen.getByText(/Usa al menos/)).toBeInTheDocument();
  });

  it("rechaza una contraseña nueva igual a la actual", async () => {
    setup();
    await fill(VALID, VALID, VALID);
    await submit();
    expect(screen.getByText("La contraseña nueva debe ser distinta de la actual.")).toBeInTheDocument();
  });

  it("exige que la repetición coincida", async () => {
    setup();
    await fill("actual-clave-1", VALID, VALID + "x");
    await submit();
    expect(screen.getByText("Las contraseñas no coinciden.")).toBeInTheDocument();
  });

  it("al guardar manda las contraseñas y muestra la confirmación", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);
    const { onClose } = setup();

    await fill("actual-clave-1", VALID, VALID);
    await submit();

    expect(await screen.findByRole("status")).toHaveTextContent("Tu contraseña se cambió");
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ currentPassword: "actual-clave-1", newPassword: VALID });
    await userEvent.click(screen.getByRole("button", { name: "Cerrar" }));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("muestra el mensaje del servidor si rechaza el cambio", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ title: "Bad", detail: "la actual no coincide" }), {
          status: 400,
          headers: { "Content-Type": "application/json" },
        }),
      ),
    );
    setup();
    await fill("actual-clave-1", VALID, VALID);
    await submit();
    expect(await screen.findByText(/la actual no coincide/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Cambiar contraseña" })).toBeEnabled();
  });
});
