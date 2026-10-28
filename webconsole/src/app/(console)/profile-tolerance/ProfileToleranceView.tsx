"use client";

import { useState } from "react";
import { useConsole } from "@/components/console/ConsoleProvider";
import { PageHeading, LoadError } from "@/components/console/Ui";
import { ProfileToleranceForm } from "@/components/console/ProfileToleranceForm";
import { ProfileToleranceHistory } from "@/components/console/ProfileToleranceHistory";
import { errorMessage, useLoad } from "@/lib/console/hooks";
import { fetchProfileTolerance, type ProfileTolerance } from "@/lib/console/profileTolerance";

export function ProfileToleranceView() {
  const { user, token } = useConsole();
  const isAdmin = user.role === "ADMIN";
  // The API answers 403 to anyone but an administrator, so nobody else loads it.
  const tolerance = useLoad(isAdmin ? "tolerancia-perfil" : null, fetchProfileTolerance);
  const [notice, setNotice] = useState<string | null>(null);

  const changed = (next: ProfileTolerance) => {
    setNotice(`La tolerancia ahora es ${next.tolerancePercent} %. Se aplica desde el siguiente movimiento.`);
    tolerance.update(() => next);
  };

  return (
    <div className="anim-stagger flex max-w-[860px] min-w-0 flex-col gap-5">
      <PageHeading title="Tolerancia del perfil">
        Cuánto puede superar la actividad real a lo que la persona declaró antes de generar una alerta de desviación.
      </PageHeading>

      {!isAdmin && (
        <div className="border border-line-mid bg-soft px-4 py-3 text-[15px] leading-[22px]">
          <strong>Solo administradores.</strong> Tu rol no puede ver ni cambiar la tolerancia.
        </div>
      )}

      {notice && (
        <div
          role="status"
          className="anim-notice flex items-start justify-between gap-3 border border-[#0b6b4a] bg-[#e2f5ec] px-4 py-3 text-[15px] leading-[22px] font-semibold text-[#0b4f37]"
        >
          <span className="[overflow-wrap:anywhere]">✓ {notice}</span>
          <button
            type="button"
            className="-my-1 flex-none rounded-full px-2 text-lg leading-none hover:bg-black/5"
            aria-label="Cerrar aviso"
            onClick={() => setNotice(null)}
          >
            ×
          </button>
        </div>
      )}

      {tolerance.loading && (
        <div className="card flex flex-col gap-3" role="status" aria-label="Cargando tolerancia">
          <div className="skel w-40" />
          <div className="skel w-2/3" />
        </div>
      )}
      {tolerance.error && (
        <div className="card p-0!">
          <LoadError
            title="No pudimos cargar la tolerancia"
            text={errorMessage(tolerance.error, "Hubo un problema con el servidor. Intenta de nuevo en un momento.")}
            onRetry={tolerance.reload}
          />
        </div>
      )}
      {tolerance.data && (
        <>
          <ProfileToleranceForm
            key={tolerance.data.tolerancePercent}
            tolerance={tolerance.data}
            token={token}
            onChanged={changed}
          />
          <section className="flex min-w-0 flex-col gap-3" aria-labelledby="h-bitacora">
            <h2 id="h-bitacora" className="m-0 font-display text-lg font-extrabold">
              Bitácora de cambios
            </h2>
            <ProfileToleranceHistory changes={tolerance.data.changes} />
          </section>
        </>
      )}
    </div>
  );
}
