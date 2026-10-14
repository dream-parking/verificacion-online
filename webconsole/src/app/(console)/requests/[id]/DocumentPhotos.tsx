"use client";

import { useEffect, useState } from "react";
import { useSession, signOut } from "@/lib/console/session";
import { ApiError, fetchDocumentPhoto, type DocumentSide } from "@/lib/console/api";

type PhotoState = { url?: string; error?: ApiError };

const SIDES: { side: DocumentSide; label: string }[] = [
  { side: "FRONT", label: "Frente" },
  { side: "BACK", label: "Reverso" },
];

function Photo({ requestId, side, label }: { requestId: string; side: DocumentSide; label: string }) {
  const token = useSession()?.accessToken ?? null;
  const [state, setState] = useState<PhotoState>();

  useEffect(() => {
    if (!token) return;
    let isCurrent = true;
    let objectUrl: string | undefined;
    fetchDocumentPhoto(requestId, side, token).then(
      (blob) => {
        objectUrl = URL.createObjectURL(blob);
        if (isCurrent) setState({ url: objectUrl });
        else URL.revokeObjectURL(objectUrl);
      },
      (e: unknown) => {
        if (!isCurrent) return;
        const error = e instanceof ApiError ? e : new ApiError(0, "Error", String(e));
        if (error.status === 401) signOut();
        setState({ error });
      },
    );
    return () => {
      isCurrent = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [requestId, side, token]);

  let body;
  if (!state) {
    body = <div className="skel h-48! w-full" role="status" aria-label={`Cargando foto: ${label}`} />;
  } else if (state.url) {
    body = (
      <a href={state.url} target="_blank" rel="noopener noreferrer" title="Abrir en tamaño completo">
        {/* eslint-disable-next-line @next/next/no-img-element -- blob URL created from an authenticated request */}
        <img
          src={state.url}
          alt={`DUI, ${label.toLowerCase()}`}
          className="block max-h-80 w-full rounded-lg border border-line-soft bg-black/5 object-contain"
        />
      </a>
    );
  } else {
    body = (
      <p className="m-0 text-[15px] text-muted">
        {state.error?.status === 404
          ? "La persona aún no ha enviado esta foto."
          : state.error?.status === 403
            ? "Tu rol no tiene permiso para ver esta foto."
            : "No pudimos cargar la foto. Intenta de nuevo en un momento."}
      </p>
    );
  }

  return (
    <figure className="m-0 min-w-0 flex-[1_1_300px]">
      <figcaption className="k mb-1.5">{label}</figcaption>
      {body}
    </figure>
  );
}

export function DocumentPhotos({ requestId }: { requestId: string }) {
  return (
    <div className="flex flex-wrap gap-5">
      {SIDES.map((s) => (
        <Photo key={s.side} requestId={requestId} {...s} />
      ))}
    </div>
  );
}
