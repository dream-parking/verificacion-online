import { Suspense } from "react";
import { SolicitudesView } from "./SolicitudesView";

export default function SolicitudesPage() {
  return (
    <Suspense>
      <SolicitudesView />
    </Suspense>
  );
}
