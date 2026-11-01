// Build-time check of the API URL (VDI-140). `next.config.ts` runs it: NEXT_PUBLIC_API_URL is inlined into the bundle
// when the console is built, so a deployed build without it would silently call another environment.

const LOCAL_HOST = /^https?:\/\/(localhost|127\.0\.0\.1|\[::1\])(:|\/|$)/i;

/**
 * The deploy workflow sets DEPLOY_ENV (dev or qa). A build with it must come with NEXT_PUBLIC_API_URL, and not
 * pointing to the local machine. Builds without DEPLOY_ENV (local development, the CI build) are not checked.
 */
export function assertApiUrl(env: Record<string, string | undefined>) {
  const target = env.DEPLOY_ENV?.trim();
  if (!target) return;

  const url = env.NEXT_PUBLIC_API_URL?.trim();
  if (!url) {
    throw new Error(
      `Falta NEXT_PUBLIC_API_URL en el build de ${target}. La consola no tiene una URL de respaldo: ` +
        "defínela en webconsole-deploy.yml con la URL del API de ese ambiente.",
    );
  }
  if (LOCAL_HOST.test(url)) {
    throw new Error(`NEXT_PUBLIC_API_URL apunta a la máquina local (${url}) en el build de ${target}.`);
  }
}
