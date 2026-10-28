"use client";

import type { ProfileToleranceChange } from "@/lib/console/profileTolerance";
import { formatDateTime } from "@/lib/console/format";

/** Log of changes to the profile tolerance (VDI-138), newest first. Entries are never edited or deleted. */
export function ProfileToleranceHistory({ changes }: { changes: ProfileToleranceChange[] }) {
  return (
    <div className="tblwrap" role="table" aria-label="Bitácora de cambios de la tolerancia">
      <div className="gh g-tolerance" role="row">
        <div role="columnheader">Cuándo</div>
        <div role="columnheader">Antes</div>
        <div role="columnheader">Ahora</div>
        <div role="columnheader">Cambiada por</div>
      </div>
      {changes.map((c) => (
        <div key={c.changedAt} className="gr g-tolerance" role="row">
          <div role="cell">{formatDateTime(c.changedAt)}</div>
          <div role="cell">{c.previousPercent == null ? <span className="text-muted">—</span> : `${c.previousPercent} %`}</div>
          <div role="cell">
            <strong>{c.tolerancePercent} %</strong>
          </div>
          <div role="cell">{c.changedBy ?? <span className="text-muted">Valor inicial</span>}</div>
        </div>
      ))}
    </div>
  );
}
