import Image from "next/image";
// Las imágenes de marca ya están optimizadas (public/marca): se sirven tal cual.

/** Marca de la barra lateral: emblema redondo y nombre del banco, sobre fondo oscuro. */
export function Logo() {
  return (
    <span className="flex min-w-0 items-center gap-2.5">
      <Image
        src="/marca/emblema.png"
        alt=""
        width={44}
        height={44}
        priority
        unoptimized
        className="size-11 flex-none rounded-full bg-white ring-2 ring-brand"
      />
      <span className="flex min-w-0 flex-col leading-none">
        <span className="font-marca text-[10px] font-semibold tracking-[0.32em] text-brand">BANCO</span>
        <span className="font-marca text-[14px] font-bold text-white">TANGAMANDAPIO</span>
      </span>
    </span>
  );
}

/** Logo completo (con el cartero y el lema), para fondos claros. */
export function LogoCompleto({ className = "" }: { className?: string }) {
  return (
    <Image
      src="/marca/logo-tangamandapio.webp"
      alt="Banco Tangamandapio — Confianza que nos une, futuro que construimos"
      width={640}
      height={548}
      priority
      unoptimized
      className={className}
    />
  );
}
