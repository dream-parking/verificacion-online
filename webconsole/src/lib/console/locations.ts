// Location of every request, for the map screen, in one call (VDI-109). The map used to read the detail of each
// request, but the detail carries the DUI data and every read of it is recorded in the access audit.

import { apiFetch, type RequestStatus, type RiskLevel } from "./api";

/** One row of `GET /api/console/requests/locations`. */
export type RequestLocation = {
  id: string;
  number: string | null;
  name: string | null;
  riskLevel: RiskLevel;
  status: RequestStatus;
  ip: string | null;
  locationStatus: "AVAILABLE" | "UNAVAILABLE" | null;
  latitude: number | null;
  longitude: number | null;
  city: string | null;
  regionName: string | null;
  country: string | null;
  approximateLocation: string | null;
};

export type LocatedRequest = {
  id: string;
  requestNumber: string | null;
  name: string | null;
  riskLevel: RiskLevel;
  status: RequestStatus;
  ip: string | null;
  place: string | null;
  latitude: number | null;
  longitude: number | null;
  /** The server could place this IP on the map. */
  located: boolean;
};

type Place = Pick<RequestLocation, "city" | "regionName" | "country" | "approximateLocation">;

/** City, region and country as the server resolved them; the app's own text is only a fallback. */
export function placeName(p: Place): string | null {
  const resolved = [...new Set([p.city, p.regionName, p.country].filter(Boolean))].join(", ");
  return resolved || p.approximateLocation || null;
}

export async function fetchLocations(token: string): Promise<LocatedRequest[]> {
  const rows = await apiFetch<RequestLocation[]>("/api/console/requests/locations", { token });
  return rows.map((r) => {
    const located = r.locationStatus === "AVAILABLE" && r.latitude != null && r.longitude != null;
    return {
      id: r.id,
      requestNumber: r.number,
      name: r.name,
      riskLevel: r.riskLevel,
      status: r.status,
      ip: r.ip,
      place: placeName(r),
      latitude: located ? r.latitude : null,
      longitude: located ? r.longitude : null,
      located,
    };
  });
}

/** Requests that share a spot (about 100 m) are drawn as one marker. */
export type PlaceGroup = {
  key: string;
  latitude: number;
  longitude: number;
  place: string | null;
  members: LocatedRequest[];
  /** Highest risk among the members: it sets the marker color. */
  riskLevel: RiskLevel;
};

const RISK_ORDER: RiskLevel[] = ["NOT_EVALUATED", "LOW", "PENDING_REVIEW", "MEDIUM", "HIGH"];

export const groupKey = (latitude: number, longitude: number) => `${latitude.toFixed(3)},${longitude.toFixed(3)}`;

export function groupByPlace(items: LocatedRequest[]): PlaceGroup[] {
  const groups = new Map<string, PlaceGroup>();
  for (const item of items) {
    if (!item.located) continue;
    const key = groupKey(item.latitude!, item.longitude!);
    const group = groups.get(key);
    if (!group) {
      groups.set(key, {
        key,
        latitude: item.latitude!,
        longitude: item.longitude!,
        place: item.place,
        members: [item],
        riskLevel: item.riskLevel,
      });
      continue;
    }
    group.members.push(item);
    if (RISK_ORDER.indexOf(item.riskLevel) > RISK_ORDER.indexOf(group.riskLevel)) group.riskLevel = item.riskLevel;
  }
  return [...groups.values()];
}

/** Marker colors by risk. The table under the map repeats the level in words. */
export const RISK_MARKER: Record<RiskLevel, { fill: string; stroke: string }> = {
  NOT_EVALUATED: { fill: "#ffffff", stroke: "#5b5b5b" },
  LOW: { fill: "#00448c", stroke: "#00448c" },
  PENDING_REVIEW: { fill: "#989696", stroke: "#4a4a4a" },
  MEDIUM: { fill: "#fff0c2", stroke: "#7a4b00" },
  HIGH: { fill: "#b24a00", stroke: "#7a2e00" },
};
