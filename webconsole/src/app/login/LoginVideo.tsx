"use client";

import { useSyncExternalStore } from "react";

// El panel de la derecha solo existe desde 1024 px (lg) y el video pesa unos 3,4 MB: en teléfonos
// y tabletas no se descarga. Con «reducir movimiento» activado tampoco se reproduce: queda la foto.
const CONSULTA = "(min-width: 1024px) and (prefers-reduced-motion: no-preference)";

function suscribir(avisar: () => void) {
  const mq = window.matchMedia(CONSULTA);
  mq.addEventListener("change", avisar);
  return () => mq.removeEventListener("change", avisar);
}

/** Video decorativo en bucle, sin sonido. Se coloca sobre la foto, que se ve mientras carga. */
export function LoginVideo({ src, poster }: { src: string; poster: string }) {
  const reproducir = useSyncExternalStore(
    suscribir,
    () => window.matchMedia(CONSULTA).matches,
    () => false,
  );
  if (!reproducir) return null;
  return (
    <video
      className="anim-aparecer absolute inset-0 size-full object-cover object-[50%_40%]"
      src={src}
      poster={poster}
      autoPlay
      loop
      muted
      playsInline
      preload="auto"
      disablePictureInPicture
      aria-hidden="true"
      tabIndex={-1}
    />
  );
}
