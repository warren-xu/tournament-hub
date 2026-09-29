"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api, ApiCallError } from "@/lib/client-api";

/** Admin-only, before the draft starts. The captain loses their seat and isn't requeued. */
export function DeleteTeam({ teamId, name }: { teamId: number; name: string }) {
  const router = useRouter();
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function remove() {
    setBusy(true);
    setError(null);
    try {
      await api(`/api/teams/${teamId}`, { method: "DELETE" });
      router.refresh();
    } catch (err) {
      setError(err instanceof ApiCallError ? err.message : "Couldn't delete the team.");
      setBusy(false);
    }
  }

  return (
    <div className="mt-4 border-t border-line-soft pt-3 text-xs">
      {confirming ? (
        <p className="flex flex-wrap items-center gap-3 text-muted">
          <span>Delete {name}?</span>
          <button type="button" disabled={busy} onClick={remove}
            className="text-signal underline underline-offset-4 hover:text-bone disabled:opacity-40">
            {busy ? "Deleting…" : "Yes, delete"}
          </button>
          <button type="button" disabled={busy} onClick={() => setConfirming(false)}
            className="underline underline-offset-4 hover:text-bone">
            Keep it
          </button>
        </p>
      ) : (
        <button type="button" onClick={() => setConfirming(true)}
          className="text-muted underline underline-offset-4 hover:text-signal">
          Delete team
        </button>
      )}
      {error ? <p role="alert" className="mt-2 text-sm text-signal">{error}</p> : null}
    </div>
  );
}
