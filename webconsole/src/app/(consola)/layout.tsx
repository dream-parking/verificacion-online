import type { ReactNode } from "react";
import { ConSesion } from "@/components/consola/ConsolaProvider";
import { Shell } from "@/components/consola/Shell";

export default function ConsolaLayout({ children }: { children: ReactNode }) {
  return (
    <ConSesion>
      <Shell>{children}</Shell>
    </ConSesion>
  );
}
