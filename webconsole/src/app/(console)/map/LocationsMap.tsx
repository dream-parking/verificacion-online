"use client";

import { useEffect, useRef, useState } from "react";
import type { CircleMarker, LayerGroup, Map as LeafletMap } from "leaflet";
import "leaflet/dist/leaflet.css";
import { RISK } from "@/lib/console/format";
import { RISK_MARKER, type PlaceGroup } from "@/lib/console/locations";

const MAX_NAMES_IN_POPUP = 8;

/** Points to focus: `n` changes on every click so the same place can be focused twice. */
export type MapFocus = { key: string; n: number } | null;

// Names and places come from people, so everything is written as text (never as HTML).
function popupContent(group: PlaceGroup, onOpen: (id: string) => void): HTMLElement {
  const root = document.createElement("div");
  root.className = "locations-popup";

  const title = document.createElement("strong");
  title.textContent = group.place ?? "Ubicación aproximada";
  root.append(title);

  const list = document.createElement("ul");
  for (const member of group.members.slice(0, MAX_NAMES_IN_POPUP)) {
    const item = document.createElement("li");
    const link = document.createElement("button");
    link.type = "button";
    link.className = "locations-link";
    link.textContent = `${member.name || "Sin nombre aún"} · ${member.requestNumber ?? "Sin número aún"}`;
    link.addEventListener("click", () => onOpen(member.id));
    const risk = document.createElement("span");
    risk.textContent = RISK[member.riskLevel].label;
    item.append(link, risk);
    list.append(item);
  }
  root.append(list);

  if (group.members.length > MAX_NAMES_IN_POPUP) {
    const more = document.createElement("p");
    more.textContent = `y ${group.members.length - MAX_NAMES_IN_POPUP} más`;
    root.append(more);
  }
  return root;
}

export function LocationsMap({
  groups,
  focus,
  onOpen,
}: {
  groups: PlaceGroup[];
  focus: MapFocus;
  onOpen: (id: string) => void;
}) {
  const container = useRef<HTMLDivElement>(null);
  const map = useRef<LeafletMap | null>(null);
  const layer = useRef<LayerGroup | null>(null);
  const markers = useRef(new Map<string, CircleMarker>());
  const leaflet = useRef<typeof import("leaflet") | null>(null);
  const [ready, setReady] = useState(false);

  // Create the map once. Leaflet needs the browser, so it is loaded here and not on the server.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      const leafletModule = await import("leaflet");
      const L = (leafletModule as unknown as { default?: typeof import("leaflet") }).default ?? leafletModule;
      if (cancelled || !container.current) return;
      leaflet.current = L;
      map.current = L.map(container.current, { worldCopyJump: true }).setView([13.7, -89.2], 6);
      L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
        maxZoom: 18,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright" rel="noopener noreferrer">OpenStreetMap</a>',
      }).addTo(map.current);
      layer.current = L.layerGroup().addTo(map.current);
      setReady(true);
    })();
    const created = markers.current;
    return () => {
      cancelled = true;
      map.current?.remove();
      map.current = null;
      layer.current = null;
      created.clear();
      setReady(false);
    };
  }, []);

  // Draw one marker per place and fit the view to all of them.
  useEffect(() => {
    const L = leaflet.current;
    if (!ready || !L || !map.current || !layer.current) return;
    layer.current.clearLayers();
    markers.current.clear();
    for (const group of groups) {
      const color = RISK_MARKER[group.riskLevel];
      const marker = L.circleMarker([group.latitude, group.longitude], {
        radius: 8 + Math.min(group.members.length, 10) * 1.5,
        color: color.stroke,
        weight: 2,
        fillColor: color.fill,
        fillOpacity: 0.85,
      });
      marker.bindPopup(popupContent(group, onOpen), { maxWidth: 300 });
      const label = document.createElement("span");
      label.textContent = `${group.place ?? "Ubicación aproximada"} · ${group.members.length} ${
        group.members.length === 1 ? "solicitud" : "solicitudes"
      }`;
      marker.bindTooltip(label);
      marker.addTo(layer.current);
      markers.current.set(group.key, marker);
    }
    if (groups.length > 0) {
      map.current.fitBounds(L.latLngBounds(groups.map((g) => [g.latitude, g.longitude] as [number, number])), {
        padding: [40, 40],
        maxZoom: 11,
      });
    }
  }, [groups, ready, onOpen]);

  // Zoom to a place when asked from the table.
  useEffect(() => {
    if (!focus || !map.current) return;
    const marker = markers.current.get(focus.key);
    if (!marker) return;
    map.current.flyTo(marker.getLatLng(), Math.max(map.current.getZoom(), 11), { duration: 0.6 });
    marker.openPopup();
  }, [focus]);

  return (
    <div
      ref={container}
      role="region"
      aria-label="Mapa con la ubicación aproximada de cada solicitud"
      className="isolate h-[480px] w-full border border-line-mid bg-soft"
    />
  );
}
