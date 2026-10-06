export function Logo({ size = "md" }: { size?: "md" | "lg" }) {
  const lg = size === "lg";
  return (
    <span className="flex items-center gap-2.5">
      <span
        className={`flex items-center justify-center rounded-full bg-brand ${lg ? "size-8" : "size-[30px]"}`}
        aria-hidden="true"
      >
        <span className={`rounded-full bg-ink ${lg ? "size-3" : "size-[11px]"}`} />
      </span>
      <span className={`font-display font-extrabold tracking-[-0.3px] ${lg ? "text-[26px]" : "text-2xl"}`}>
        Ceiba
      </span>
    </span>
  );
}
