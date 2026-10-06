"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { LogoCompleto } from "@/components/consola/Logo";
import { ApiError, login } from "@/lib/consola/api";
import { iniciarSesion, useSesion } from "@/lib/consola/sesion";

function mensajeLogin(e: unknown) {
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
  const sesion = useSesion();
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState("");

  // Con una sesión vigente no tiene sentido volver a entrar.
  useEffect(() => {
    if (sesion) router.replace("/solicitudes");
  }, [sesion, router]);

  async function entrar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const datos = new FormData(e.currentTarget);
    setEnviando(true);
    setError("");
    try {
      iniciarSesion(await login(String(datos.get("email")).trim(), String(datos.get("password"))));
      router.replace("/solicitudes");
    } catch (err) {
      setError(mensajeLogin(err));
      setEnviando(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-dark px-4 py-8">
      <form
        className="anim-pagina w-full max-w-[440px] bg-white px-6 pt-8 pb-7 sm:px-9 sm:pt-10 sm:pb-9"
        onSubmit={entrar}
      >
        <div className="mb-6 flex flex-col items-center gap-2 border-b border-line pb-6">
          <LogoCompleto className="h-auto w-[230px] sm:w-[260px]" />
          <span className="text-[13px] font-semibold tracking-[0.12em] text-muted uppercase">Consola de verificación</span>
        </div>
        <h1 className="mt-0 mb-6 font-display text-[26px] leading-[1.15] font-extrabold sm:text-[28px]">
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
            className="anim-aviso mt-5 mb-0 border border-danger bg-[#fbeaea] px-4 py-3 text-sm leading-5 font-semibold text-danger"
          >
            {error}
          </p>
        )}
        <div className="mt-7">
          <button type="submit" className="btn min-h-[52px] w-full text-base" disabled={enviando}>
            {enviando ? "Ingresando…" : "Ingresar"}
          </button>
        </div>
        <p className="mt-4 mb-0 text-sm leading-5 text-muted">
          ¿Olvidaste tu contraseña? Pide a un administrador de la consola que la restablezca.
        </p>
      </form>
    </div>
  );
}
