"use client";

import type { ScoreModelVersion } from "@/lib/console/api";
import { formatDateTime } from "@/lib/console/format";

/**
 * Version history of the score model (VDI-121): thresholds, who published each version and when,
 * newest first, with the one in force marked. No version is ever edited or deleted.
 */
export function ScoreModelHistory({ versions }: { versions: ScoreModelVersion[] }) {
  return (
    <div className="tblwrap" role="table" aria-label="Historial de versiones del modelo">
      <div className="gh g-versions" role="row">
        <div role="columnheader">Versión</div>
        <div role="columnheader">Bajo</div>
        <div role="columnheader">Medio</div>
        <div role="columnheader">Alto</div>
        <div role="columnheader">Publicada por</div>
        <div role="columnheader">Vigencia</div>
      </div>
      {versions.map((v) => (
        <div key={v.version} className="gr g-versions" role="row" aria-current={v.inForce ? "true" : undefined}>
          <div role="cell" className="flex flex-wrap items-center gap-2">
            <strong>v{v.version}</strong>
            {v.inForce && <span className="badge s-ok">✓ Vigente</span>}
          </div>
          <div role="cell">
            {v.low.from} a {v.low.to}
          </div>
          <div role="cell">
            {v.medium.from} a {v.medium.to}
          </div>
          <div role="cell">
            {v.high.from} a {v.high.to}
          </div>
          <div role="cell">{v.publishedBy ?? <span className="text-muted">Sistema (migración)</span>}</div>
          <div role="cell" className="text-[13px] leading-[18px]">
            {v.validFrom ? `Desde ${formatDateTime(v.validFrom)}` : "—"}
            {v.validTo && (
              <>
                <br />
                <span className="text-muted">Hasta {formatDateTime(v.validTo)}</span>
              </>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}
