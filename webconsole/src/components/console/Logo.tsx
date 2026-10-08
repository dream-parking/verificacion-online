import Image from "next/image";
// Brand images are already optimized (public/brand): they are served as they are.

/** Sidebar brand: round emblem and bank name, on a dark background. */
export function Logo() {
  return (
    <span className="flex min-w-0 items-center gap-2.5">
      <Image
        src="/brand/emblem.png"
        alt=""
        width={44}
        height={44}
        priority
        unoptimized
        className="size-11 flex-none rounded-full bg-white ring-2 ring-brand"
      />
      <span className="flex min-w-0 flex-col leading-none">
        <span className="font-brand text-[10px] font-semibold tracking-[0.32em] text-brand">BANCO</span>
        <span className="font-brand text-[14px] font-bold text-white">TANGAMANDAPIO</span>
      </span>
    </span>
  );
}

/** Full logo (with the postman and the slogan), for light backgrounds. */
export function FullLogo({ className = "" }: { className?: string }) {
  return (
    <Image
      src="/brand/logo-tangamandapio.webp"
      alt="Banco Tangamandapio — Confianza que nos une, futuro que construimos"
      width={640}
      height={548}
      priority
      unoptimized
      className={className}
    />
  );
}
