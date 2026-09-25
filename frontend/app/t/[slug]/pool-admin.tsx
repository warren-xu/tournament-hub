"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Avatar, Tag } from "@/components/ui";
import { api, ApiCallError } from "@/lib/client-api";
import type { RegistrationStatus, RegistrationView } from "@/lib/types";

/** Admin view of the pool: approve or reject each sign-up inline. */
export function PoolAdmin({ registrations }: { registrations: RegistrationView[] }) {
  const router = useRouter();
  const [busy, setBusy] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function setStatus(id: number, status: RegistrationStatus) {
    setBusy(id);
    setError(null);
    try {
      await api(`/api/tournaments/registrations/${id}?status=${status}`, {
        method: "PUT",
      });
      router.refresh();
    } catch (err) {
      setError(err instanceof ApiCallError ? err.message : "Update failed.");
    } finally {
      setBusy(null);
    }
  }

  return (
    <div>
      {error ? (
        <p role="alert" className="mb-3 text-sm text-signal">
          {error}
        </p>
      ) : null}

      <ul className="border border-line">
        {registrations.map((r, i) => (
          <li
            key={r.id}
            className={`flex flex-wrap items-center gap-4 bg-panel px-4 py-3 ${
              i > 0 ? "border-t border-line-soft" : ""
            }`}
          >
            <Avatar src={r.player.avatarUrl} name={r.player.username} />

            <div className="min-w-40 flex-1">
              <p className="text-sm text-bone">{r.player.username}</p>
              <p className="truncate font-mono text-xs text-dim">
                {r.player.riotId ?? "no riot id"}
              </p>
            </div>

            <div className="tabular flex min-w-48 gap-5 text-sm text-muted">
              <span>{r.player.currentRank ?? "—"}</span>
              <span>{r.player.primaryRole ?? "—"}</span>
            </div>

            <Tag tone={r.status === "APPROVED" ? "accent" : "outline"}>
              {r.status}
            </Tag>

            <div className="flex gap-2">
              <button
                onClick={() => setStatus(r.id, "APPROVED")}
                disabled={busy === r.id || r.status === "APPROVED"}
                className="border border-line px-3 py-1 font-display text-xs font-semibold uppercase tracking-wider text-muted transition-colors hover:border-dim hover:text-bone disabled:opacity-30"
              >
                Approve
              </button>
              <button
                onClick={() => setStatus(r.id, "REJECTED")}
                disabled={busy === r.id || r.status === "REJECTED"}
                className="border border-line px-3 py-1 font-display text-xs font-semibold uppercase tracking-wider text-muted transition-colors hover:border-accent-deep hover:text-signal disabled:opacity-30"
              >
                Reject
              </button>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
