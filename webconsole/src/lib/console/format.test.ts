import { describe, expect, it } from "vitest";
import {
  canTakeAlerts,
  duration,
  formatDate,
  formatDateTime,
  formatShortDate,
  formatTime,
  isSubmitted,
  REQUEST_STATUS,
  deviceLocationText,
  locationFailureText,
  minSec,
  profileTimeline,
  usd,
} from "./format";
import type { ProfileVersion } from "./api";

describe("fechas en hora de El Salvador (UTC−6)", () => {
  // 2026-10-06T04:08:03Z son las 10:08:03 p. m. del 5 de octubre en El Salvador.
  const iso = "2026-10-06T04:08:03Z";

  it("formatea la fecha con el día del huso de El Salvador", () => {
    expect(formatDate(iso)).toBe("05/10/2026");
  });
  it("formatea la hora en 12 horas con p. m.", () => {
    expect(formatTime(iso)).toBe("10:08 p. m.");
  });
  it("formatea fecha corta y fecha completa", () => {
    expect(formatShortDate(iso)).toBe("05/10 10:08 p. m.");
    expect(formatDateTime(iso)).toBe("05/10/2026 10:08:03 p. m.");
  });
  it("usa a. m. por la mañana", () => {
    expect(formatTime("2026-10-06T14:05:00Z")).toBe("8:05 a. m.");
  });
});

describe("duraciones", () => {
  it("minSec rellena los segundos con cero", () => {
    expect(minSec(68)).toBe("1:08");
    expect(minSec(0)).toBe("0:00");
  });
  it("duration escribe minutos y segundos", () => {
    expect(duration(308)).toBe("5 min 08 s");
  });
});

describe("usd", () => {
  it("separa miles y usa USD por defecto", () => {
    expect(usd(12500)).toBe("USD 12,500");
    expect(usd(300, "EUR")).toBe("EUR 300");
  });
});

describe("estado de la solicitud", () => {
  it("la revisión reforzada tiene texto y símbolo, no solo color", () => {
    expect(REQUEST_STATUS.ENHANCED_REVIEW).toEqual({ cls: "s-enhanced", label: "⚑ Revisión reforzada" });
  });
  it("la espera del RNPN se muestra como «En espera», con símbolo", () => {
    expect(REQUEST_STATUS.AWAITING_RNPN).toEqual({ cls: "s-waiting", label: "◷ En espera" });
  });
  it("una solicitud en revisión reforzada ya se envió", () => {
    expect(isSubmitted("COMPLETED")).toBe(true);
    expect(isSubmitted("ENHANCED_REVIEW")).toBe(true);
    expect(isSubmitted("AWAITING_RNPN")).toBe(true);
    expect(isSubmitted("IN_PROGRESS")).toBe(false);
    expect(isSubmitted("ABANDONED")).toBe(false);
  });
});

describe("canTakeAlerts", () => {
  it("solo fraude y administración toman alertas", () => {
    expect(canTakeAlerts("FRAUD_ANALYST")).toBe(true);
    expect(canTakeAlerts("ADMIN")).toBe(true);
    expect(canTakeAlerts("KYC_LEAD")).toBe(false);
  });
});

describe("locationFailureText", () => {
  it.each([
    ["private range", "privada o reservada"],
    ["Reserved range", "privada o reservada"],
    ["lookup disabled", "desactivada"],
    ["provider timeout", "proveedor de ubicación"],
    ["algo raro", "No se pudo ubicar"],
    [null, "No se pudo ubicar"],
    [undefined, "No se pudo ubicar"],
  ])("%s", (failure, expected) => {
    expect(locationFailureText(failure)).toContain(expected);
  });
});

describe("deviceLocationText", () => {
  it("muestra la ciudad que reportó el teléfono", () => {
    expect(deviceLocationText("AVAILABLE", "Santa Ana, El Salvador")).toBe("Santa Ana, El Salvador");
  });
  it.each([
    ["DENIED", "no dio permiso"],
    ["UNAVAILABLE", "no pudo obtenerla"],
  ] as const)("sin permiso o sin ciudad (%s) es «Ubicación no disponible»", (status, detail) => {
    const text = deviceLocationText(status, "Santa Ana");
    expect(text).toContain("Ubicación no disponible");
    expect(text).toContain(detail);
    expect(text).not.toContain("Santa Ana");
  });
  it("una app anterior no la envía", () => {
    expect(deviceLocationText(null, null)).toContain("Sin dato");
    expect(deviceLocationText(undefined, undefined)).toContain("Sin dato");
  });
});

describe("profileTimeline", () => {
  const version = (n: number, extra: Partial<ProfileVersion> = {}): ProfileVersion => ({
    version: n,
    current: false,
    transactionTypeCode: "PAGO_SALARIO",
    transactionTypeLabel: "Salario y transferencias",
    monthlyAmountRangeCode: "R1",
    monthlyAmountRangeLabel: "Menos de USD 500",
    changedBy: "CUSTOMER",
    declaredAt: "2026-10-09T15:49:00Z",
    ...extra,
  });

  it("ordena de la más nueva a la más vieja y solo la última es la vigente", () => {
    const list = profileTimeline([version(1), version(2, { current: true }), version(3)]);
    expect(list.map((e) => e.version)).toEqual([3, 2, 1]);
    expect(list.map((e) => e.current)).toEqual([true, false, false]);
  });

  it("pone la fecha y la hora de El Salvador, y desde la versión 2 quién la cambió", () => {
    const [second, first] = profileTimeline([version(1), version(2, { declaredAt: "2026-10-09T15:51:00Z" })]);
    expect(first.when).toBe("09/10/2026 · 9:49 a. m.");
    expect(second.when).toBe("09/10/2026 · 9:51 a. m. · cambiada por la persona antes de enviar");
  });

  it("no dice quién la cambió si el API no lo informa", () => {
    const [second] = profileTimeline([version(1), version(2, { changedBy: null })]);
    expect(second.when).not.toContain("cambiada");
  });

  it("usa la etiqueta del tipo de dinero y del monto, o el código si falta", () => {
    const [entry] = profileTimeline([version(1)]);
    expect(entry).toMatchObject({ transactionType: "Salario y transferencias", monthlyAmount: "Menos de USD 500" });

    const [bare] = profileTimeline([version(1, { transactionTypeLabel: null, monthlyAmountRangeLabel: null })]);
    expect(bare).toMatchObject({ transactionType: "PAGO_SALARIO", monthlyAmount: "R1" });
  });

  it("sin versiones devuelve una lista vacía", () => {
    expect(profileTimeline([])).toEqual([]);
    expect(profileTimeline(null)).toEqual([]);
    expect(profileTimeline(undefined)).toEqual([]);
  });
});
