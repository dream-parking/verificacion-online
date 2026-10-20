import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ScoreModel } from "@/lib/console/api";
import { ScoreThresholdsForm } from "./ScoreThresholdsForm";

const MODEL: ScoreModel = {
  version: 2,
  name: "Score v2",
  low: { from: 0, to: 39 },
  medium: { from: 40, to: 69 },
  high: { from: 70, to: 100 },
  validFrom: "2026-10-01T12:00:00Z",
  weights: [],
};

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

function setup() {
  const onPublished = vi.fn();
  render(<ScoreThresholdsForm model={MODEL} token="tok" onPublished={onPublished} />);
  return { onPublished };
}

const low = () => screen.getByLabelText("Fin del nivel Bajo");
const medium = () => screen.getByLabelText("Fin del nivel Medio");
const submit = () => userEvent.click(screen.getByRole("button", { name: "Publicar umbrales" }));

async function type(field: HTMLElement, value: string) {
  await userEvent.clear(field);
  if (value) await userEvent.type(field, value);
}

afterEach(() => vi.unstubAllGlobals());

describe("ScoreThresholdsForm", () => {
  it("empieza con los umbrales vigentes", () => {
    setup();
    expect(low()).toHaveValue("39");
    expect(medium()).toHaveValue("69");
  });

  it("muestra cómo quedarían los rangos mientras se escribe", async () => {
    setup();
    await type(low(), "29");
    expect(screen.getByText(/Quedaría:/)).toHaveTextContent("Bajo de 0 a 29, Medio de 30 a 69 y Alto de 70 a 100");
  });

  it("un valor inválido no se puede enviar", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    setup();
    await type(low(), "70");
    await submit();

    expect(screen.getByText("Debe ser menor que el fin del nivel Medio.")).toBeInTheDocument();
    expect(low()).toHaveAttribute("aria-invalid", "true");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("no deja publicar los mismos umbrales", async () => {
    setup();
    await submit();
    expect(screen.getByRole("alert")).toHaveTextContent(/mismos umbrales/);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("pide confirmación explicando que aplica a las evaluaciones siguientes", async () => {
    setup();
    await type(medium(), "59");
    await submit();

    const dialog = screen.getByRole("dialog", { name: "¿Publicar la versión 3?" });
    expect(dialog).toHaveTextContent(/a partir de ahora/);
    expect(dialog).toHaveTextContent(/conservan su resultado/);
    const rows = within(dialog).getAllByRole("row");
    expect(rows[2]).toHaveTextContent("40 a 6940 a 59");
    expect(rows[3]).toHaveTextContent("70 a 10060 a 100");
    expect(within(dialog).getByRole("button", { name: "Cancelar" })).toHaveFocus();
  });

  it("Cancelar y Escape cierran la confirmación sin publicar", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    setup();
    await type(medium(), "59");
    await submit();
    await userEvent.click(screen.getByRole("button", { name: "Cancelar" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    await submit();
    await userEvent.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("al confirmar publica los umbrales y avisa", async () => {
    const published = { ...MODEL, version: 3, validTo: null, inForce: true, publishedBy: "Ana" };
    const fetchMock = vi.fn().mockResolvedValue(json(published, 201));
    vi.stubGlobal("fetch", fetchMock);
    const { onPublished } = setup();

    await type(low(), "30");
    await type(medium(), "60");
    await submit();
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Publicar umbrales" }));

    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ lowMax: 30, mediumMax: 60 });
    expect(onPublished).toHaveBeenCalledWith(published);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("muestra en su campo el error que manda la API", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(json({ title: "Bad", errors: { mediumMax: "El fin del nivel Medio no es válido" } }, 400)),
    );
    const { onPublished } = setup();
    await type(medium(), "59");
    await submit();
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Publicar umbrales" }));

    expect(await screen.findByText("El fin del nivel Medio no es válido")).toBeInTheDocument();
    expect(medium()).toHaveAttribute("aria-invalid", "true");
    expect(onPublished).not.toHaveBeenCalled();

    // Editing the field clears the API message.
    await userEvent.type(medium(), "8");
    expect(screen.queryByText("El fin del nivel Medio no es válido")).not.toBeInTheDocument();
  });

  it("muestra un mensaje general si el servidor no responde", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("fail")));
    setup();
    await type(medium(), "59");
    await submit();
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Publicar umbrales" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/conectar con el servidor/);
    expect(screen.getByRole("button", { name: "Publicar umbrales" })).toBeEnabled();
  });
});
