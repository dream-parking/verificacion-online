import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("next/image", () => ({
  // eslint-disable-next-line @next/next/no-img-element
  default: ({ alt, src }: { alt: string; src: string }) => <img alt={alt} src={src} />,
}));

import { FullLogo, Logo } from "./Logo";

describe("logos", () => {
  it("Logo muestra el nombre del banco y un emblema decorativo", () => {
    const { container } = render(<Logo />);
    expect(screen.getByText("BANCO")).toBeInTheDocument();
    expect(screen.getByText("TANGAMANDAPIO")).toBeInTheDocument();
    expect(container.querySelector("img")).toHaveAttribute("alt", "");
  });

  it("FullLogo describe la marca con su lema y acepta clases", () => {
    render(<FullLogo className="w-40" />);
    const img = screen.getByRole("img", { name: /Banco Tangamandapio/ });
    expect(img).toHaveAttribute("src", "/brand/logo-tangamandapio.webp");
  });
});
