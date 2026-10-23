import { act, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CustomerNumber } from "./CustomerNumber";

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
});

describe("CustomerNumber", () => {
  it.each([undefined, null, ""])("sin número dice «Sin número de cliente» (%s)", (value) => {
    render(<CustomerNumber value={value} />);
    expect(screen.getByText("Sin número de cliente")).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("muestra el número y lo copia al portapapeles", async () => {
    const user = userEvent.setup();
    const writeText = vi.spyOn(navigator.clipboard, "writeText").mockResolvedValue();
    render(<CustomerNumber value="CLI-0000001" />);

    expect(screen.getByText("CLI-0000001")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Copiar el número de cliente CLI-0000001" }));

    expect(writeText).toHaveBeenCalledWith("CLI-0000001");
    expect(screen.getByRole("status")).toHaveTextContent("✓ Copiado");
  });

  it("avisa si no se pudo copiar", async () => {
    const user = userEvent.setup();
    vi.spyOn(navigator.clipboard, "writeText").mockRejectedValue(new Error("denied"));
    render(<CustomerNumber value="CLI-0000001" />);

    await user.click(screen.getByRole("button", { name: /Copiar/ }));
    expect(screen.getByRole("status")).toHaveTextContent("No se pudo copiar");
  });

  it("la confirmación desaparece después de un momento", async () => {
    vi.useFakeTimers();
    vi.spyOn(navigator.clipboard, "writeText").mockResolvedValue();
    render(<CustomerNumber value="CLI-0000001" />);

    // Let the clipboard promise settle before checking.
    await act(async () => fireEvent.click(screen.getByRole("button", { name: /Copiar/ })));
    expect(screen.getByRole("status")).toHaveTextContent("Copiado");
    act(() => vi.advanceTimersByTime(2000));
    expect(screen.getByRole("status")).toHaveTextContent("");
  });
});
