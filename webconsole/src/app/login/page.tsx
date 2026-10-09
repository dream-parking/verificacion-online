"use client";

import Image from "next/image";
import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { FullLogo } from "@/components/console/Logo";
import { ApiError, login } from "@/lib/console/api";
import { startSession, useSession } from "@/lib/console/session";
import { LoginVideo } from "./LoginVideo";

function loginErrorMessage(e: unknown) {
  if (!(e instanceof ApiError)) return "Algo salió mal. Intenta de nuevo.";
  if (e.status === 0) return "No pudimos conectar con el servidor. Revisa tu conexión e intenta de nuevo.";
  if (e.status === 401 || e.status === 400) return "El correo o la contraseña no son correctos.";
  if (e.status === 429) {
    const min = e.retryAfterSeconds ? Math.ceil(e.retryAfterSeconds / 60) : 15;
    return `Demasiados intentos fallidos. Espera ${min} ${min === 1 ? "minuto" : "minutos"} e intenta de nuevo.`;
  }
  return "Algo salió mal. Intenta de nuevo.";
}

export default function LoginPage() {
  const router = useRouter();
  const session = useSession();
  const [sending, setSending] = useState(false);
  const [error, setError] = useState("");

  // With a valid session there is no point in signing in again.
  useEffect(() => {
    if (session) router.replace("/requests");
  }, [session, router]);

  async function signIn(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = new FormData(e.currentTarget);
    setSending(true);
    setError("");
    try {
      startSession(await login(String(data.get("email")).trim(), String(data.get("password"))));
      router.replace("/requests");
    } catch (err) {
      setError(loginErrorMessage(err));
      setSending(false);
    }
  }

  return (
    // Split screen: form on the left and image on the right (only from 1024 px).
    <div className="grid min-h-screen bg-white lg:grid-cols-[minmax(420px,36%)_1fr]">
      <main className="flex min-h-screen flex-col px-6 py-8 sm:px-12">
        <div className="flex flex-1 items-center justify-center py-6">
          <form className="anim-page w-full max-w-[400px]" onSubmit={signIn}>
            <div className="mb-8 flex flex-col items-center gap-2">
              <FullLogo className="h-auto w-[220px] sm:w-[250px]" />
              <span className="text-[13px] font-semibold tracking-[0.12em] text-muted uppercase">
                Consola de verificación
              </span>
            </div>
            <h1 className="mt-0 mb-6 text-center font-display text-[26px] leading-[1.15] font-extrabold sm:text-[28px]">
              Ingresa a tu cuenta de trabajo
            </h1>
            <div className="flex flex-col gap-5">
              <div>
                <label htmlFor="l-mail" className="lbl">
                  Correo institucional
                </label>
                <input
                  id="l-mail"
                  name="email"
                  className="fld"
                  type="email"
                  autoComplete="username"
                  placeholder="nombre@empresa.com"
                  required
                  aria-invalid={!!error}
                  aria-describedby={error ? "l-error" : undefined}
                />
              </div>
              <div>
                <label htmlFor="l-pass" className="lbl">
                  Contraseña
                </label>
                <input
                  id="l-pass"
                  name="password"
                  className="fld"
                  type="password"
                  autoComplete="current-password"
                  placeholder="••••••••"
                  required
                  aria-invalid={!!error}
                  aria-describedby={error ? "l-error" : undefined}
                />
              </div>
            </div>
            {error && (
              <p
                id="l-error"
                role="alert"
                className="anim-notice mt-5 mb-0 border border-danger bg-[#fbeaea] px-4 py-3 text-sm leading-5 font-semibold text-danger"
              >
                {error}
              </p>
            )}
            <div className="mt-7">
              <button type="submit" className="btn min-h-[52px] w-full text-base" disabled={sending}>
                {sending ? "Ingresando…" : "Ingresar"}
              </button>
            </div>
            <p className="mt-5 mb-0 text-center text-sm leading-5 text-muted">
              ¿Olvidaste tu contraseña? Pide a un administrador de la consola que la restablezca.
            </p>
          </form>
        </div>
        <p className="m-0 text-center text-[13px] text-muted">Confianza que nos une, futuro que construimos</p>
      </main>

      {/* Decorative image: it adds no information that is not already in the form. */}
      <aside className="relative hidden overflow-hidden bg-dark lg:block" aria-hidden="true">
        <Image
          src="/brand/login-postman.webp"
          alt=""
          fill
          priority
          unoptimized
          sizes="64vw"
          className="anim-fade object-cover object-[50%_40%]"
        />
        <LoginVideo src="/brand/login-postman.mp4" poster="/brand/login-postman.webp" />
      </aside>
    </div>
  );
}
