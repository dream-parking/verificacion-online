// Small OpenStreetMap embed around an approximate location. No API key is needed; the coordinates
// are only sent to openstreetmap.org by the analyst's browser, to draw the tiles.

/** Degrees shown around the point: about a city. An IP lookup is not more precise than that. */
const SPAN = 0.12;

export function LocationMap({ latitude, longitude, place }: { latitude: number; longitude: number; place: string | null }) {
  const lat = latitude.toFixed(5);
  const lon = longitude.toFixed(5);
  const bbox = [longitude - SPAN, latitude - SPAN * 0.6, longitude + SPAN, latitude + SPAN * 0.6]
    .map((n) => n.toFixed(5))
    .join("%2C");
  const embed = `https://www.openstreetmap.org/export/embed.html?bbox=${bbox}&layer=mapnik&marker=${lat}%2C${lon}`;
  const link = `https://www.openstreetmap.org/?mlat=${lat}&mlon=${lon}#map=11/${lat}/${lon}`;

  return (
    <figure className="m-0 min-w-0">
      <iframe
        title={`Mapa de la ubicación aproximada${place ? `: ${place}` : ""}`}
        src={embed}
        loading="lazy"
        referrerPolicy="no-referrer"
        className="block h-[280px] w-full border border-line-mid bg-soft"
      />
      <figcaption className="mt-2 text-[13px] leading-[18px] text-muted">
        La ubicación sale de la dirección IP: es aproximada (la zona de la ciudad) y puede ser la del proveedor de
        internet, no la de la persona.{" "}
        <a href={link} target="_blank" rel="noopener noreferrer" className="font-semibold text-blue underline">
          Abrir en OpenStreetMap
        </a>
      </figcaption>
    </figure>
  );
}
