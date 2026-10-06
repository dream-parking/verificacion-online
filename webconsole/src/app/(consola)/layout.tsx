import type { ReactNode } from "react";
import { ConsolaProvider } from "@/components/consola/ConsolaProvider";
import { Shell } from "@/components/consola/Shell";

export default function ConsolaLayout({ children }: { children: ReactNode }) {
  return (
    <ConsolaProvider>
      <Shell>{children}</Shell>
    </ConsolaProvider>
  );
}
