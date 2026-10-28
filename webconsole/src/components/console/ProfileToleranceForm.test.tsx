import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ProfileTolerance } from "@/lib/console/profileTolerance";
import { ProfileToleranceForm } from "./ProfileToleranceForm";

const TOLERANCE: ProfileTolerance = { tolerancePercent: 20, minPercent: 0, maxPercent: 45, changes: [] };

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

function setup() {
  const onChanged = vi.fn();
  render(<ProfileToleranceForm tolerance={TOLERANCE} token="tok" onChanged={onChanged} />);
  return { onChanged };
}

const field = () => screen.getByLabelText("Tolerancia (%)");
const save = () => userEvent.click(screen.getByRole("button", { name: "Guardar tolerancia" }));

async function type(value: string) {
  await userEvent.clear(field());
  if (value) await userEvent.type(field(), value);
}

afterEach(() => vi.unstubAllGlobals());

describe("ProfileToleranceForm", () => {
  it("empieza con la tolerancia vigente y dice el rango permitido", () => {
    setup();
    expect(field()).toHaveValue("20");
    expect(screen.getByText("Entero de 0 a 45. Hoy es 20.")).toBeInTheDocument();
  });

  it("un valor inválido no llama a la API y explica el error", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    const { onChanged } = setup();
    await type("50");
    await save();
    expect(screen.getByRole("alert")).toHaveTextContent("Debe estar entre 0 y 45.");
    expect(fetchMock).not.toHaveBeenCalled();
    expect(onChanged).not.toHaveBeenCalled();
  });

  it("guarda la tolerancia nueva y avisa con la respuesta de la API", async () => {
    const next = { ...TOLERANCE, tolerancePercent: 30 };
    const fetchMock = vi.fn().mockResolvedValue(json(next));
    vi.stubGlobal("fetch", fetchMock);
    const { onChanged } = setup();
    await type("30");
    await save();
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ tolerancePercent: 30 });
    expect(onChanged).toHaveBeenCalledWith(next);
  });

  it("muestra el mensaje en español que manda la API cuando rechaza el valor", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ title: "Bad", errors: { tolerancePercent: "No puede ser mayor que 45." } }), {
          status: 400,
          headers: { "Content-Type": "application/problem+json" },
        }),
      ),
    );
    const { onChanged } = setup();
    await type("30");
    await save();
    expect(await screen.findByRole("alert")).toHaveTextContent("No puede ser mayor que 45.");
    expect(onChanged).not.toHaveBeenCalled();
  });
});
