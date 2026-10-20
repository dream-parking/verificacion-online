import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "./api";
import { customerNumberOf, fetchCustomerRequestIds, isPartialCustomerNumber } from "./customers";

afterEach(() => vi.unstubAllGlobals());

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

describe("customerNumberOf", () => {
  it("acepta CLI- con siete dígitos o más, sin importar mayúsculas ni espacios", () => {
    expect(customerNumberOf("CLI-0000001")).toBe("CLI-0000001");
    expect(customerNumberOf("  cli-0000042 ")).toBe("CLI-0000042");
    expect(customerNumberOf("CLI-12345678")).toBe("CLI-12345678");
  });

  it.each(["", "CLI-", "CLI-000001", "CLI-00000a1", "SOL-2026-00041", "0000001"])("rechaza «%s»", (text) => {
    expect(customerNumberOf(text)).toBeNull();
  });
});

describe("isPartialCustomerNumber", () => {
  it("detecta un número de cliente a medio escribir", () => {
    expect(isPartialCustomerNumber("cli-00")).toBe(true);
    expect(isPartialCustomerNumber(" CLI")).toBe(true);
  });
  it("no aplica a un número completo ni a otras búsquedas", () => {
    expect(isPartialCustomerNumber("CLI-0000001")).toBe(false);
    expect(isPartialCustomerNumber("SOL-2026")).toBe(false);
    expect(isPartialCustomerNumber("")).toBe(false);
  });
});

describe("fetchCustomerRequestIds", () => {
  it("pide el historial del cliente y devuelve los ids de sus solicitudes", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      json({ customerId: "c1", customerNumber: "CLI-0000001", requests: [{ requestId: "r1" }, { requestId: "r2" }] }),
    );
    vi.stubGlobal("fetch", fetchMock);

    expect(await fetchCustomerRequestIds("tok", "CLI-0000001")).toEqual(["r1", "r2"]);
    expect(fetchMock.mock.calls[0][0]).toMatch(/\/api\/console\/customers\/risk-history\?number=CLI-0000001$/);
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe("Bearer tok");
  });

  it("devuelve null cuando ningún cliente tiene ese número", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json({ title: "Not Found" }, 404)));
    expect(await fetchCustomerRequestIds("tok", "CLI-0000099")).toBeNull();
  });

  it("propaga los demás errores", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json({ title: "Boom" }, 500)));
    await expect(fetchCustomerRequestIds("tok", "CLI-0000001")).rejects.toBeInstanceOf(ApiError);
  });
});
