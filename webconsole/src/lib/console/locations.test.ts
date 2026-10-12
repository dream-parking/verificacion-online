import { afterEach, describe, expect, it, vi } from "vitest";
import type { RequestDetail } from "./api";
import { fetchLocations, groupByPlace, groupKey, placeName, type LocatedRequest } from "./locations";

const json = (body: unknown) => new Response(JSON.stringify(body), { headers: { "Content-Type": "application/json" } });

afterEach(() => vi.unstubAllGlobals());

describe("placeName", () => {
  const signals = (s: Partial<NonNullable<RequestDetail["signals"]>>) => s as RequestDetail["signals"];

  it("junta ciudad, región y país sin repetir", () => {
    const s = signals({ ipDetails: { city: "San Salvador", regionName: "San Salvador", country: "El Salvador" } as never });
    expect(placeName(s)).toBe("San Salvador, El Salvador");
  });
  it("usa la ubicación aproximada de la app si el servidor no resolvió nada", () => {
    expect(placeName(signals({ approximateLocation: "Santa Tecla" }))).toBe("Santa Tecla");
  });
  it("devuelve null sin señales", () => {
    expect(placeName(null)).toBeNull();
  });
});

describe("groupByPlace", () => {
  const item = (id: string, lat: number | null, lng: number | null, risk: LocatedRequest["riskLevel"]): LocatedRequest => ({
    id,
    requestNumber: null,
    name: null,
    riskLevel: risk,
    status: "COMPLETED",
    ip: null,
    place: "Lugar",
    latitude: lat,
    longitude: lng,
    located: lat !== null,
  });

  it("agrupa los puntos a ~100 m y toma el mayor riesgo", () => {
    const groups = groupByPlace([
      item("a", 13.6929, -89.2182, "LOW"),
      item("b", 13.69291, -89.21819, "HIGH"),
      item("c", 14.0, -89.0, "MEDIUM"),
    ]);
    expect(groups).toHaveLength(2);
    expect(groups[0].members.map((m) => m.id)).toEqual(["a", "b"]);
    expect(groups[0].riskLevel).toBe("HIGH");
    expect(groups[1].riskLevel).toBe("MEDIUM");
  });
  it("no baja el riesgo con un miembro de menor nivel", () => {
    const [g] = groupByPlace([item("a", 1, 1, "HIGH"), item("b", 1, 1, "NOT_EVALUATED")]);
    expect(g.riskLevel).toBe("HIGH");
  });
  it("ignora las solicitudes sin ubicar", () => {
    expect(groupByPlace([item("a", null, null, "LOW")])).toEqual([]);
  });
  it("groupKey redondea a 3 decimales", () => {
    expect(groupKey(13.69294, -89.21816)).toBe("13.693,-89.218");
  });
});

describe("fetchLocations", () => {
  it("lee el detalle de cada solicitud y marca cuáles se ubicaron", async () => {
    const summaries = ["r1", "r2", "r3"].map((id) => ({
      id,
      number: `N-${id}`,
      name: id,
      riskLevel: "LOW",
      status: "COMPLETED",
    }));
    const details: Record<string, unknown> = {
      r1: { signals: { ip: "1.1.1.1", locationStatus: "AVAILABLE", latitude: 13.7, longitude: -89.2, approximateLocation: "SV" } },
      r2: { signals: { ip: "10.0.0.1", locationStatus: "UNAVAILABLE", latitude: 1, longitude: 1 } },
      r3: { signals: null },
    };
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation(async (url: string) => {
        if (url.includes("/requests?")) {
          return json({ content: summaries, page: 0, size: 100, totalElements: 3, totalPages: 1 });
        }
        return json(details[decodeURIComponent(url.split("/").pop()!)]);
      }),
    );

    const result = await fetchLocations("tok");

    expect(result.map((r) => r.id)).toEqual(["r1", "r2", "r3"]);
    expect(result[0]).toMatchObject({ located: true, latitude: 13.7, longitude: -89.2, place: "SV", ip: "1.1.1.1" });
    expect(result[1]).toMatchObject({ located: false, latitude: null, longitude: null });
    expect(result[2]).toMatchObject({ located: false, ip: null, place: null });
  });
});
