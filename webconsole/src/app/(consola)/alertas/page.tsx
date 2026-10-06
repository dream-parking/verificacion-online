import { Suspense } from "react";
import { AlertasView } from "./AlertasView";

export default function AlertasPage() {
  return (
    <Suspense>
      <AlertasView />
    </Suspense>
  );
}
