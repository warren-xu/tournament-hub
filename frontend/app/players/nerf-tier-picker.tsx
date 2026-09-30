"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api, ApiCallError } from "@/lib/client-api";
import { NERFS } from "@/lib/nerfs";
import type { NerfTier } from "@/lib/types";

/** Admins set a player's nerf here. It only affects the draft room; the profile is untouched. */
export function NerfTierPicker({
  profileId,
  username,
  tier,
}: {
  profileId: number;
  username: string;
  tier: NerfTier | null;
}) {
  const router = useRouter();
  const [value, setValue] = useState<NerfTier | "">(tier ?? "");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function change(next: NerfTier | "") {
    const previous = value;
    setValue(next);
    setPending(true);
    setError(null);
    try {
      await api(`/api/profiles/${profileId}/nerf`, { method: "PUT", json: { tier: next || null } });
      router.refresh();
    } catch (err) {
      setValue(previous);
      setError(err instanceof ApiCallError ? err.message : "Could not update the nerf.");
    } finally {
      setPending(false);
    }
  }

  return (
    <span className="flex flex-col gap-1">
      <label className="flex items-center gap-2 font-display text-[0.6875rem] font-semibold uppercase tracking-widest text-dim">
        Nerf
        <select
          value={value}
          disabled={pending}
          onChange={(e) => void change(e.target.value as NerfTier | "")}
          aria-label={`Nerf for ${username}`}
          className="border border-line bg-ink px-2 py-1 text-xs normal-case tracking-normal text-bone focus:border-accent focus:outline-none disabled:opacity-40"
        >
          <option value="">None</option>
          {(Object.keys(NERFS) as NerfTier[]).map((key) => (
            <option key={key} value={key}>
              {NERFS[key].label}: {NERFS[key].rules.join(", ")}
            </option>
          ))}
        </select>
      </label>
      {error ? <span role="alert" className="text-xs text-signal">{error}</span> : null}
    </span>
  );
}
