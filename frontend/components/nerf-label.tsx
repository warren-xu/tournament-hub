import { NERFS } from "@/lib/nerfs";
import type { NerfTier } from "@/lib/types";

/** A player's nerf, spelled out, for the draft room. Renders nothing without one. */
export function NerfLabel({ tier }: { tier: NerfTier | null | undefined }) {
  if (!tier) return null;
  const nerf = NERFS[tier];
  return (
    <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
      <span className="border border-signal px-1.5 py-0.5 font-display text-xs font-semibold uppercase tracking-wider text-signal">
        {nerf.label} nerf
      </span>
      <span className="text-muted">{nerf.rules.join(" · ")}</span>
    </p>
  );
}
