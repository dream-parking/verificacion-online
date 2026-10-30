"use client";

import type { RequestDetail } from "@/lib/console/api";
import { formatDateTime, usd } from "@/lib/console/format";
import { KeyValue, type Pair } from "./Ui";

const money = (n: number | null | undefined) => (n == null ? "—" : usd(n));
const percent = (n: number | null | undefined) =>
  n == null ? "—" : `${n.toLocaleString("es", { maximumFractionDigits: 2 })} % anual`;

/**
 * The product an approved request chose (VDI-150): its name, conditions and when it was asked for. It is read only;
 * the person chooses it in the app. Without a choice the card says so.
 */
export function ProductRequestCard({ request }: { request: RequestDetail["productRequest"] | undefined }) {
  const product = request?.product;
  if (!request || !product) {
    return (
      <div>
        <p className="m-0 text-[15px] font-semibold">Sin solicitud de producto</p>
        <p className="mt-1 mb-0 text-sm leading-5 text-muted">
          La persona todavía no ha elegido un producto. Puede hacerlo desde la app cuando su solicitud está aprobada.
        </p>
      </div>
    );
  }

  const rows: Pair[] = [
    { k: "Producto", v: <strong>{product.name ?? product.code ?? "—"}</strong> },
    ...(product.description ? [{ k: "Qué ofrece", v: product.description }] : []),
    { k: "Interés", v: percent(product.annualInterestRate) },
    { k: "Cuota mensual", v: product.monthlyFeeUsd === 0 ? "Sin cuota mensual" : money(product.monthlyFeeUsd) },
    { k: "Depósito para abrir", v: product.minimumOpeningDepositUsd === 0 ? "Sin depósito mínimo" : money(product.minimumOpeningDepositUsd) },
    { k: "Solicitado el", v: request.requestedAt ? formatDateTime(request.requestedAt) : "—" },
  ];

  return <KeyValue items={rows} />;
}
