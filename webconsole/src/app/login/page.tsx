"use client";

import { useRouter } from "next/navigation";
import { Logo } from "@/components/consola/Logo";

export default function LoginPage() {
  const router = useRouter();

  return (
    <div className="flex min-h-screen items-center justify-center bg-dark px-4 py-8">
      <form
        className="anim-pagina w-full max-w-[440px] bg-white px-6 pt-8 pb-7 sm:px-9 sm:pt-10 sm:pb-9"
        onSubmit={(e) => {
          e.preventDefault();
          router.push("/solicitudes");
        }}
      >
        <div className="mb-7 flex items-center gap-2.5">
          <Logo size="lg" />
          <span className="ml-0.5 text-sm text-muted">Consola de verificación</span>
        </div>
        <h1 className="mt-0 mb-6 font-display text-[28px] leading-[1.15] font-extrabold">
          Ingresa a tu cuenta de trabajo
        </h1>
        <div className="flex flex-col gap-5">
          <div>
            <label htmlFor="l-mail" className="lbl">
              Correo institucional
            </label>
            <input id="l-mail" className="fld" type="email" autoComplete="username" placeholder="nombre@ceiba.example" />
          </div>
          <div>
            <label htmlFor="l-pass" className="lbl">
              Contraseña
            </label>
            <input id="l-pass" className="fld" type="password" autoComplete="current-password" placeholder="••••••••" />
          </div>
        </div>
        <div className="mt-7">
          <button type="submit" className="btn min-h-[52px] w-full text-base">
            Ingresar
          </button>
        </div>
        <p className="mt-4 mb-0 text-sm leading-5 text-muted">Prototipo: puedes entrar con cualquier dato.</p>
      </form>
    </div>
  );
}
