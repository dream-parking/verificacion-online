import type { ProfileVersion } from "@/lib/console/api";
import { NO_PROFILE, profileTimeline } from "@/lib/console/format";

/** Every version of the declared transactional profile, newest first; the one in force is marked (VDI-3). */
export function ProfileVersions({ versions }: { versions: ProfileVersion[] | null | undefined }) {
  const entries = profileTimeline(versions);
  if (entries.length === 0) return <p className="m-0 text-[15px] text-muted">{NO_PROFILE}</p>;

  return (
    <ol className="m-0 flex list-none flex-col p-0">
      {entries.map((e, i) => (
        <li key={e.version} className="grid grid-cols-[28px_1fr] gap-x-3.5">
          <div className="flex flex-col items-center" aria-hidden="true">
            <span
              className={`mt-1 box-border h-3.5 w-3.5 flex-none rounded-full border-[3px] ${
                e.current ? "border-[#0a402b] bg-brand" : "border-line-strong bg-white"
              }`}
            />
            <span className={`w-0.5 flex-1 ${i < entries.length - 1 ? "bg-line" : "bg-transparent"}`} />
          </div>
          <div className="flex min-w-0 flex-col gap-2 pb-[18px]">
            <div className="flex flex-wrap items-center gap-x-2.5 gap-y-1">
              <span className="text-base font-bold">Versión {e.version}</span>
              {e.current && <span className="badge s-ok">Vigente</span>}
              <span className="text-sm text-muted">{e.when}</span>
            </div>
            <div className="flex flex-wrap gap-x-10 gap-y-2 text-[15px]">
              <span>
                <span className="text-muted">Tipo de dinero:</span> {e.transactionType}
              </span>
              <span>
                <span className="text-muted">Monto mensual:</span> <strong>{e.monthlyAmount}</strong>
              </span>
            </div>
          </div>
        </li>
      ))}
    </ol>
  );
}
