import { describe, expect, it } from "vitest";
import {
  canTakeAlerts,
  duration,
  formatDate,
  formatDateTime,
  formatShortDate,
  formatTime,
  locationFailureText,
  minSec,
  usd,
} from "./format";

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
