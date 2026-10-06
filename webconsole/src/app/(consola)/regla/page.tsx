import Link from "next/link";
import { Encabezado } from "@/components/consola/ui";

export default function ReglaPage() {
  return (
    <div className="anim-escalonado flex max-w-[860px] min-w-0 flex-col gap-5">
      <Encabezado titulo="Regla de score">Así se asigna hoy el nivel de riesgo a cada solicitud.</Encabezado>

      <div className="flex items-center gap-3 border border-line-mid bg-soft px-4 py-3 text-[15px] leading-[22px]">
        <svg
          className="flex-none"
          width="22"
          height="22"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <rect x="5" y="11" width="14" height="10" rx="2" />
          <path d="M8 11V7a4 4 0 0 1 8 0v4" />
        </svg>
        <span>
          <strong>Solo lectura.</strong> Esta regla no se puede editar en esta versión.
        </span>
      </div>

      <section className="card" aria-labelledby="h-regla">
        <h2 id="h-regla" className="mt-0 mb-4 font-display text-lg font-extrabold">
          Regla vigente
        </h2>
        <p className="mt-0 mb-5 font-display text-2xl leading-[1.25] font-extrabold">
          Si el monto mensual declarado es menor a USD 500, el riesgo es bajo.
        </p>
        <dl className="kv m-0">
          <dt className="k">Identificador</dt>
          <dd className="v m-0">R-01 · Monto mensual bajo</dd>
          <dt className="k">Umbral</dt>
          <dd className="v m-0">USD 500 al mes</dd>
          <dt className="k">Resultado</dt>
          <dd className="v m-0">
            <span className="badge b-bajo">● Bajo</span>
          </dd>
          <dt className="k">Si el monto es de USD 500 o más</dt>
          <dd className="v m-0">
            Queda como «Pendiente de evaluación»: en este sprint la regla solo asigna riesgo bajo.
          </dd>
          <dt className="k">Última actualización</dt>
          <dd className="v m-0">02/10/2026</dd>
          <dt className="k">Estado del umbral</dt>
          <dd className="v m-0">Valor provisional, pendiente de confirmar con Conozca a su Cliente.</dd>
        </dl>
      </section>

      <section className="card" aria-labelledby="h-ej">
        <h2 id="h-ej" className="mt-0 mb-2 font-display text-lg font-extrabold">
          Un ejemplo real
        </h2>
        <p className="mt-0 mb-3.5 text-base leading-6">
          Marta Alejandra Rivas Cruz declaró USD 320 al mes. Es menor a USD 500, así que su solicitud quedó con
          riesgo bajo.
        </p>
        <Link href="/solicitudes/s1" className="btn2">
          Ver esa solicitud
        </Link>
      </section>
    </div>
  );
}
