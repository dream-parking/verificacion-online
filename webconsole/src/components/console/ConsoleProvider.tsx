"use client";

import { useRouter } from "next/navigation";
import { createContext, useContext, useEffect, type ReactNode } from "react";
import { apiFetch, ApiError, type Alert, type ConsoleUser } from "@/lib/console/api";
import { signOut, useSession } from "@/lib/console/session";
import { useLoad } from "@/lib/console/hooks";

type ConsoleContext = {
  user: ConsoleUser;
  token: string;
  /** Open alerts: used by the inbox and the side menu counter. */
  alerts: ReturnType<typeof useLoad<Alert[]>>;
  /** The alert is assigned to the session user. Returns the updated alert. */
  takeAlert: (id: string) => Promise<Alert>;
};

const Ctx = createContext<ConsoleContext | null>(null);

/** Only mounted with a valid session (see Shell). */
export function ConsoleProvider({
  user,
  token,
  children,
}: {
  user: ConsoleUser;
  token: string;
  children: ReactNode;
}) {
  const alerts = useLoad("alertas", (t) => apiFetch<Alert[]>("/api/console/alerts", { token: t }));

  const takeAlert = async (id: string) => {
    try {
      const alert = await apiFetch<Alert>(`/api/console/alerts/${id}/take`, { token, method: "POST" });
      alerts.update((list) => list.map((x) => (x.id === id ? alert : x)));
      return alert;
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) signOut();
      // Another person took it or it changed status: refresh to show the real state.
      if (e instanceof ApiError && e.status === 409) alerts.reload();
      throw e;
    }
  };

  return <Ctx.Provider value={{ user, token, alerts, takeAlert }}>{children}</Ctx.Provider>;
}

export function useConsole() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useConsole must be used inside <ConsoleProvider>");
  return ctx;
}

/**
 * Requires a session: without one it redirects to the login. While the session is being read
 * (first render on the server) it renders nothing, so the console is not shown to someone who
 * has not signed in.
 */
export function WithSession({ children }: { children: ReactNode }) {
  const session = useSession();
  const router = useRouter();

  useEffect(() => {
    if (session === null) router.replace("/login");
  }, [session, router]);

  if (!session) return null;
  return (
    <ConsoleProvider user={session.user} token={session.accessToken}>
      {children}
    </ConsoleProvider>
  );
}
