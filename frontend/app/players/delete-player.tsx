"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api, ApiCallError } from "@/lib/client-api";

/**
 * Hard delete, for clearing test entries before an event. The backend refuses when the
 * player is drafted, captains a team, or is mid-auction, and returns the reason — which
 * is shown here rather than swallowed.
 */
export function DeletePlayer({
  profileId,
  username,
}: {
  profileId: number;
  username: string;
}) {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function remove() {
    if (
      !confirm(
        `Permanently delete ${username}?\n\nThis removes their profile, agent pool, registrations and sign-in account. It cannot be undone.`,
      )
    ) {
      return;
    }

    setPending(true);
    setError(null);
    try {
      await api(`/api/profiles/${profileId}`, { method: "DELETE" });
      router.refresh();
    } catch (err) {
      setError(
        err instanceof ApiCallError ? err.message : "Could not delete this player.",
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
        aria-label={`Delete ${username}`}
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
