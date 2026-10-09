import type { ReactNode } from "react";
import { WithSession } from "@/components/console/ConsoleProvider";
import { Shell } from "@/components/console/Shell";

export default function ConsoleLayout({ children }: { children: ReactNode }) {
  return (
    <WithSession>
      <Shell>{children}</Shell>
    </WithSession>
  );
}
