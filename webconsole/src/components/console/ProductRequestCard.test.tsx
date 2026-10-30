import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { RequestDetail } from "@/lib/console/api";
import { ProductRequestCard } from "./ProductRequestCard";

type ProductRequest = NonNullable<RequestDetail["productRequest"]>;
type Product = NonNullable<ProductRequest["product"]>;

const product = (over: Partial<Product> = {}): Product => ({
  code: "AHORRO_BASICA",
  name: "Cuenta de ahorro básica",
  description: "Para empezar a ahorrar sin costo",
  annualInterestRate: 0.5,
  monthlyFeeUsd: 2.5,
  minimumOpeningDepositUsd: 10,
  ...over,
});

const request = (over: Partial<ProductRequest> = {}): ProductRequest => ({
  product: product(),
  requestedAt: "2026-10-28T15:00:00Z",
  ...over,
});

describe("ProductRequestCard", () => {
  it.each([[null], [undefined]])("sin solicitud (%s) lo dice y explica por qué", (value) => {
    render(<ProductRequestCard request={value} />);
    expect(screen.getByText("Sin solicitud de producto")).toBeInTheDocument();
    expect(screen.getByText(/todavía no ha elegido un producto/)).toBeInTheDocument();
  });

  it("muestra el producto con su nombre, sus condiciones y cuándo se pidió", () => {
    render(<ProductRequestCard request={request()} />);
    expect(screen.getByText("Cuenta de ahorro básica")).toBeInTheDocument();
    expect(screen.getByText("Para empezar a ahorrar sin costo")).toBeInTheDocument();
    expect(screen.getByText("0,5 % anual")).toBeInTheDocument();
    expect(screen.getByText("USD 2.5")).toBeInTheDocument();
    expect(screen.getByText("USD 10")).toBeInTheDocument();
    expect(screen.getByText("Solicitado el")).toBeInTheDocument();
  });

  it("una cuota o un depósito de 0 dice que no hay, en lugar de «USD 0»", () => {
    render(<ProductRequestCard request={request({ product: product({ monthlyFeeUsd: 0, minimumOpeningDepositUsd: 0 }) })} />);
    expect(screen.getByText("Sin cuota mensual")).toBeInTheDocument();
    expect(screen.getByText("Sin depósito mínimo")).toBeInTheDocument();
    expect(screen.queryByText("USD 0")).not.toBeInTheDocument();
  });

  it("si faltan datos del producto pone guiones y no se rompe", () => {
    render(
      <ProductRequestCard
        request={request({ product: product({ description: null, annualInterestRate: null, monthlyFeeUsd: null, minimumOpeningDepositUsd: null }), requestedAt: null })}
      />,
    );
    expect(screen.queryByText("Qué ofrece")).not.toBeInTheDocument();
    expect(screen.getAllByText("—")).toHaveLength(4);
  });

  it("sin nombre usa el código del producto", () => {
    render(<ProductRequestCard request={request({ product: product({ name: null }) })} />);
    expect(screen.getByText("AHORRO_BASICA")).toBeInTheDocument();
  });

  it("una solicitud sin producto (dato incompleto) se trata como sin solicitud", () => {
    render(<ProductRequestCard request={request({ product: null })} />);
    expect(screen.getByText("Sin solicitud de producto")).toBeInTheDocument();
  });
});
