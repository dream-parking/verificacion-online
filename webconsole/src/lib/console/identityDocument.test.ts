import { describe, expect, it } from "vitest";
import { duiRows } from "./identityDocument";

const read = {
  dui: "01234567-8",
  firstNames: "Marta Alejandra",
  lastNames: "Rivas Cruz",
  birthDate: "1990-03-05",
  expiryDate: "2030-11-20",
};

describe("duiRows", () => {
  it("pone lo leído junto a lo confirmado, con las fechas como día/mes/año", () => {
    const rows = duiRows({ read, confirmed: read, correctedFields: [] });

    expect(rows.map((r) => r.label)).toEqual([
      "Número de DUI",
      "Nombres",
      "Apellidos",
      "Fecha de nacimiento",
      "Fecha de vencimiento",
    ]);
    expect(rows[0]).toMatchObject({ read: "01234567-8", confirmed: "01234567-8", corrected: false });
    expect(rows[3]).toMatchObject({ read: "05/03/1990", confirmed: "05/03/1990" });
    expect(rows[4].confirmed).toBe("20/11/2030");
  });

  it("marca solo los campos que la persona corrigió", () => {
    const rows = duiRows({
      read,
      confirmed: { ...read, firstNames: "Marta Alexandra", expiryDate: "2031-11-20" },
      correctedFields: ["firstNames", "expiryDate"],
    });

    expect(rows.filter((r) => r.corrected).map((r) => r.field)).toEqual(["firstNames", "expiryDate"]);
    expect(rows[1]).toMatchObject({ read: "Marta Alejandra", confirmed: "Marta Alexandra" });
  });

  it("si no se pudo leer el DUI, lo leído son guiones y lo confirmado lo que escribió la persona", () => {
    const rows = duiRows({ read: null, confirmed: read, correctedFields: [] });

    expect(rows.every((r) => r.read === "—")).toBe(true);
    expect(rows[1].confirmed).toBe("Marta Alejandra");
    expect(rows.some((r) => r.corrected)).toBe(false);
  });

  it("si la persona aún no confirma, lo confirmado son guiones, y sin la lista de correcciones nada se marca", () => {
    const rows = duiRows({ read, confirmed: null, correctedFields: undefined as unknown as string[] });

    expect(rows.every((r) => r.confirmed === "—")).toBe(true);
    expect(rows.some((r) => r.corrected)).toBe(false);
  });

  it("deja tal cual una fecha que no viene como año-mes-día", () => {
    const rows = duiRows({ read: { ...read, birthDate: "marzo" }, confirmed: null, correctedFields: [] });
    expect(rows[3].read).toBe("marzo");
  });
});
