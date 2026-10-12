import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ComponentProps } from "react";
import { describe, expect, it, vi } from "vitest";
import { Empty, FiltersPanel, KeyValue, LoadError, Loading, PageHeading, Pagination, SelectFilter, TextFilter } from "./Ui";

describe("componentes básicos", () => {
  it("PageHeading muestra título y texto", () => {
    render(<PageHeading title="Solicitudes">Lista de solicitudes</PageHeading>);
    expect(screen.getByRole("heading", { name: "Solicitudes" })).toBeInTheDocument();
    expect(screen.getByText("Lista de solicitudes")).toBeInTheDocument();
  });

  it("KeyValue pinta cada par como término y descripción", () => {
    render(<KeyValue items={[{ k: "DUI", v: "0123" }, { k: "Teléfono", v: "7000" }]} />);
    expect(screen.getByText("DUI").tagName).toBe("DT");
    expect(screen.getByText("7000").tagName).toBe("DD");
  });

  it("Loading se anuncia como estado de carga", () => {
    render(<Loading label="solicitudes" widths={["10%", "20%"]} grid="g" />);
    expect(screen.getByRole("status", { name: "Cargando solicitudes" })).toBeInTheDocument();
  });

  it("LoadError avisa y permite reintentar", async () => {
    const onRetry = vi.fn();
    render(<LoadError title="Falló" text="Intenta otra vez" onRetry={onRetry} />);
    expect(screen.getByRole("alert")).toHaveTextContent("Falló");
    await userEvent.click(screen.getByRole("button", { name: "Reintentar" }));
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it("Empty solo ofrece quitar filtros si recibe onClear", async () => {
    const { rerender } = render(<Empty title="Nada" text="Sin datos" />);
    expect(screen.queryByRole("button")).not.toBeInTheDocument();

    const onClear = vi.fn();
    rerender(<Empty title="Nada" text="Sin datos" onClear={onClear} />);
    await userEvent.click(screen.getByRole("button", { name: "Quitar filtros" }));
    expect(onClear).toHaveBeenCalledOnce();
  });
});

describe("filtros", () => {
  it("TextFilter reporta lo que se escribe", async () => {
    const onChange = vi.fn();
    render(<TextFilter id="n" label="Nombre" value="" onChange={onChange} />);
    await userEvent.type(screen.getByLabelText("Nombre"), "ab");
    expect(onChange).toHaveBeenLastCalledWith("b");
  });

  it("SelectFilter lista las opciones y reporta la elegida", async () => {
    const onChange = vi.fn();
    render(
      <SelectFilter
        id="e"
        label="Estado"
        value="all"
        onChange={onChange}
        options={[
          { value: "all", label: "Todos" },
          { value: "open", label: "Abiertas" },
        ]}
      />,
    );
    await userEvent.selectOptions(screen.getByLabelText("Estado"), "open");
    expect(onChange).toHaveBeenCalledWith("open");
  });

  it("FiltersPanel muestra Quitar filtros solo cuando hay filtros activos", async () => {
    const onClear = vi.fn();
    const { rerender } = render(
      <FiltersPanel label="alertas" hasFilters={false} onClear={onClear}>
        x
      </FiltersPanel>,
    );
    expect(screen.getByRole("region", { name: "Filtros de alertas" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Quitar filtros" })).not.toBeInTheDocument();

    rerender(
      <FiltersPanel label="alertas" hasFilters onClear={onClear}>
        x
      </FiltersPanel>,
    );
    await userEvent.click(screen.getByRole("button", { name: "Quitar filtros" }));
    expect(onClear).toHaveBeenCalledOnce();
  });
});

describe("Pagination", () => {
  const setup = (props: Partial<ComponentProps<typeof Pagination>> = {}) => {
    const onPage = vi.fn();
    const onSize = vi.fn();
    render(
      <Pagination label="solicitudes" total={42} page={2} totalPages={9} size={5} onPage={onPage} onSize={onSize} {...props} />,
    );
    return { onPage, onSize, nav: screen.getByRole("navigation", { name: "Paginación de solicitudes" }) };
  };

  it("indica el rango visible", () => {
    const { nav } = setup();
    expect(nav).toHaveTextContent("Mostrando 6–10 de 42 solicitudes");
  });

  it("sin resultados muestra 0–0", () => {
    const { nav } = setup({ total: 0, page: 1, totalPages: 1 });
    expect(nav).toHaveTextContent("Mostrando 0–0 de 0 solicitudes");
  });

  it("resume con puntos suspensivos cuando hay muchas páginas", () => {
    const { nav } = setup({ page: 5 });
    const labels = within(nav)
      .getAllByRole("button")
      .map((b) => b.getAttribute("aria-label"));
    expect(labels).toEqual([
      "Página anterior",
      "Página 1",
      "Página 4",
      "Página 5",
      "Página 6",
      "Página 9",
      "Página siguiente",
    ]);
    expect(within(nav).getByRole("button", { name: "Página 5" })).toHaveAttribute("aria-current", "page");
  });

  it("muestra todas las páginas si son 7 o menos", () => {
    const { nav } = setup({ totalPages: 7 });
    expect(within(nav).getAllByRole("button", { name: /^Página \d$/ })).toHaveLength(7);
  });

  it("navega con anterior, siguiente y los números", async () => {
    const { onPage } = setup();
    await userEvent.click(screen.getByRole("button", { name: "Página anterior" }));
    await userEvent.click(screen.getByRole("button", { name: "Página siguiente" }));
    await userEvent.click(screen.getByRole("button", { name: "Página 9" }));
    expect(onPage.mock.calls).toEqual([[1], [3], [9]]);
  });

  it("desactiva anterior en la primera página y siguiente en la última", () => {
    setup({ page: 1, totalPages: 1 });
    expect(screen.getByRole("button", { name: "Página anterior" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Página siguiente" })).toBeDisabled();
  });

  it("cambia las filas por página", async () => {
    const { onSize } = setup();
    await userEvent.selectOptions(screen.getByLabelText("Filas por página"), "20");
    expect(onSize).toHaveBeenCalledWith(20);
  });
});
