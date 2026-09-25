"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api, ApiCallError } from "@/lib/client-api";

/**
 * Hard delete of a tournament and everything under it. The backend refuses while the
 * auction is live and returns the reason — which is shown here rather than swallowed.
 */
export function DeleteTournament({
  tournamentId,
  name,
}: {
  tournamentId: number;
  name: string;
}) {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function remove() {
    if (
      !confirm(
        `Permanently delete ${name}?\n\nThis removes its teams, registrations, auction and bid history. Player profiles are kept. It cannot be undone.`,
      )
    ) {
      return;
    }

    setPending(true);
    setError(null);
    try {
      await api(`/api/tournaments/${tournamentId}`, { method: "DELETE" });
      router.refresh();
    } catch (err) {
      setError(
        err instanceof ApiCallError ? err.message : "Could not delete this tournament.",
      );
    } finally {
      setPending(false);
    }
  }

  return (
    <span className="flex flex-col items-end gap-1">
      <button
        onClick={remove}
        disabled={pending}
        aria-label={`Delete ${name}`}
        className="font-display text-[0.6875rem] font-semibold uppercase tracking-widest text-dim transition-colors hover:text-signal disabled:opacity-40"
      >
        {pending ? "Deleting…" : "Delete"}
      </button>
      {error ? (
        <span role="alert" className="max-w-52 text-right text-xs leading-snug text-signal">
          {error}
        </span>
      ) : null}
    </span>
  );
}
