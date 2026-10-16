import type { IdentityDocument } from "@/lib/console/api";
import { duiRows } from "@/lib/console/identityDocument";

/** What the DUI says in the photo against what the person confirmed, with a mark on every field they changed (VDI-81). */
export function DuiDataTable({ document }: { document: IdentityDocument }) {
  const { read, confirmed } = document;
  if (!read && !confirmed) {
    return <p className="m-0 mb-5 text-[15px] text-muted">La persona aún no confirma los datos de su DUI.</p>;
  }

  const rows = duiRows(document);
  return (
    <div className="mb-6">
      <div className="overflow-x-auto">
        <table className="w-full min-w-[460px] border-collapse text-left text-[15px] leading-6">
          <caption className="sr-only">Datos del DUI: lo leído de la foto y lo confirmado por la persona</caption>
          <thead>
            <tr className="border-b border-line-strong text-[13px] text-muted">
              <th scope="col" className="py-2 pr-4 font-bold">
                Dato
              </th>
              <th scope="col" className="py-2 pr-4 font-bold">
                Lo leído
              </th>
              <th scope="col" className="py-2 font-bold">
                Lo confirmado
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.field} className="border-b border-line-soft align-baseline">
                <th scope="row" className="py-2.5 pr-4 font-normal text-muted">
                  {r.label}
                </th>
                <td className="py-2.5 pr-4">{r.read}</td>
                <td className="py-2.5 font-semibold">
                  {r.confirmed}
                  {r.corrected && <span className="badge c-medium ml-2.5 align-middle">Corregido</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {!read && (
        <p className="mt-3 mb-0 text-sm leading-5 text-muted">
          No se pudo leer el DUI de la foto: la persona escribió los datos a mano.
        </p>
      )}
    </div>
  );
}
