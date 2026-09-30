"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api, ApiCallError } from "@/lib/client-api";

const arrowClass =
  "grid size-8 place-items-center border border-line font-display text-sm text-muted transition-colors hover:border-dim hover:text-bone disabled:opacity-25 disabled:hover:border-line disabled:hover:text-muted";

/** Admins move a team earlier or later in the nominating order, before the draft starts. */
export function TeamOrder({
  tournamentId,
  teamIds,
  index,
  name,
}: {
  tournamentId: number;
  /** Every team, in the current order. */
  teamIds: number[];
  index: number;
  name: string;
}) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function move(by: -1 | 1) {
    const order = [...teamIds];
    const [team] = order.splice(index, 1);
    order.splice(index + by, 0, team);
    setBusy(true);
    setError(null);
    try {
      await api(`/api/tournaments/${tournamentId}/teams/order`, { method: "PUT", json: { teamIds: order } });
      router.refresh();
    } catch (err) {
      setError(err instanceof ApiCallError ? err.message : "Couldn't change the order.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <span className="flex items-center gap-1">
      <button type="button" className={arrowClass} disabled={busy || index === 0}
        onClick={() => void move(-1)} aria-label={`Move ${name} earlier`} title="Nominate earlier">
        ←
      </button>
      <button type="button" className={arrowClass} disabled={busy || index === teamIds.length - 1}
        onClick={() => void move(1)} aria-label={`Move ${name} later`} title="Nominate later">
        →
      </button>
      {error ? <span role="alert" className="ml-2 text-xs text-signal">{error}</span> : null}
    </span>
  );
}
