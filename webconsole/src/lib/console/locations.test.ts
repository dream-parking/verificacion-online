import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchLocations, groupByPlace, groupKey, placeName, type LocatedRequest } from "./locations";

const json = (body: unknown) => new Response(JSON.stringify(body), { headers: { "Content-Type": "application/json" } });

afterEach(() => vi.unstubAllGlobals());

describe("placeName", () => {
  const place = { city: null, regionName: null, country: null, approximateLocation: null };

  it("junta ciudad, región y país sin repetir", () => {
    expect(placeName({ ...place, city: "San Salvador", regionName: "San Salvador", country: "El Salvador" })).toBe(
      "San Salvador, El Salvador",
    );
  });
  it("usa la ubicación aproximada de la app si el servidor no resolvió nada", () => {
    expect(placeName({ ...place, approximateLocation: "Santa Tecla" })).toBe("Santa Tecla");
  });
  it("devuelve null sin ubicación", () => {
    expect(placeName(place)).toBeNull();
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
  it("lee todas las ubicaciones en una sola llamada y marca cuáles se ubicaron", async () => {
    const row = (id: string, extra: object) => ({
      id,
      number: `N-${id}`,
      name: id,
      riskLevel: "LOW",
      status: "COMPLETED",
      ip: null,
      locationStatus: null,
      latitude: null,
      longitude: null,
      city: null,
      regionName: null,
      country: null,
      approximateLocation: null,
      ...extra,
    });
    const fetchMock = vi.fn().mockResolvedValue(
      json([
        row("r1", { ip: "1.1.1.1", locationStatus: "AVAILABLE", latitude: 13.7, longitude: -89.2, approximateLocation: "SV" }),
        row("r2", { ip: "10.0.0.1", locationStatus: "UNAVAILABLE", latitude: 1, longitude: 1 }),
        row("r3", {}),
      ]),
    );
    vi.stubGlobal("fetch", fetchMock);

    const result = await fetchLocations("tok");

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][0]).toContain("/api/console/requests/locations");
    expect(result.map((r) => r.id)).toEqual(["r1", "r2", "r3"]);
    expect(result[0]).toMatchObject({ requestNumber: "N-r1", located: true, latitude: 13.7, longitude: -89.2, place: "SV", ip: "1.1.1.1" });
    expect(result[1]).toMatchObject({ located: false, latitude: null, longitude: null });
    expect(result[2]).toMatchObject({ located: false, ip: null, place: null });
  });
});
