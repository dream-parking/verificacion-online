// Location of every request, for the map screen. The list endpoint has no location, so the detail of
// each request is read (a few at a time). A dedicated endpoint in the API would avoid one call per request.

import { apiFetch, fetchAllRequests, type RequestDetail, type RequestStatus, type RiskLevel } from "./api";

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

/** City, region and country as the server resolved them; the app's own text is only a fallback. */
export function placeName(signals: RequestDetail["signals"]): string | null {
  const geo = signals?.ipDetails;
  const resolved = [...new Set([geo?.city, geo?.regionName, geo?.country].filter(Boolean))].join(", ");
  return resolved || signals?.approximateLocation || null;
}

const CONCURRENCY = 6;

export async function fetchLocations(token: string): Promise<LocatedRequest[]> {
  const requests = await fetchAllRequests(token);
  const result: LocatedRequest[] = new Array(requests.length);
  let next = 0;

  async function worker() {
    while (next < requests.length) {
      const index = next++;
      const request = requests[index];
      const detail = await apiFetch<RequestDetail>(`/api/console/requests/${encodeURIComponent(request.id)}`, { token });
      const s = detail.signals;
      const located = s?.locationStatus === "AVAILABLE" && s.latitude != null && s.longitude != null;
      result[index] = {
        id: request.id,
        requestNumber: request.number,
        name: request.name,
        riskLevel: request.riskLevel,
        status: request.status,
        ip: s?.ip ?? null,
        place: placeName(s),
        latitude: located ? s.latitude! : null,
        longitude: located ? s.longitude! : null,
        located,
      };
    }
  }

  await Promise.all(Array.from({ length: Math.min(CONCURRENCY, requests.length) }, worker));
  return result;
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
