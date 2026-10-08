"use client";

import { useSyncExternalStore } from "react";

// The right panel only exists from 1024 px (lg) and the video weighs about 3.4 MB: on phones and
// tablets it is not downloaded. With "reduce motion" enabled it does not play either: the photo stays.
const MEDIA_QUERY = "(min-width: 1024px) and (prefers-reduced-motion: no-preference)";

function subscribe(notify: () => void) {
  const mq = window.matchMedia(MEDIA_QUERY);
  mq.addEventListener("change", notify);
  return () => mq.removeEventListener("change", notify);
}

/** Decorative looping video, without sound. It sits over the photo, which shows while it loads. */
export function LoginVideo({ src, poster }: { src: string; poster: string }) {
  const play = useSyncExternalStore(
    subscribe,
    () => window.matchMedia(MEDIA_QUERY).matches,
    () => false,
  );
  if (!play) return null;
  return (
    <video
      className="anim-fade absolute inset-0 size-full object-cover object-[50%_40%]"
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
