"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { buttonClass, Eyebrow } from "@/components/ui";
import { api, ApiCallError } from "@/lib/client-api";
import type {
  AuctionSnapshot,
  ProfileView,
  TournamentStatus,
} from "@/lib/types";

/**
 * Everything an admin does before the draft room takes over: open sign-ups,
 * create the captains' teams, build the lot queue.
 */
export function AuctionSetup({
  tournamentId,
  tournamentStatus,
  auction,
  approvedCount,
  teamCount,
}: {
  tournamentId: number;
  tournamentStatus: TournamentStatus;
  auction: AuctionSnapshot | null;
  approvedCount: number;
  teamCount: number;
}) {
  const router = useRouter();
  const [profiles, setProfiles] = useState<ProfileView[]>([]);
  const [teamName, setTeamName] = useState("");
  const [captainUserId, setCaptainUserId] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<ProfileView[]>("/api/profiles")
      .then(setProfiles)
      .catch(() => setProfiles([]));
  }, []);

  async function run(key: string, fn: () => Promise<unknown>) {
    setBusy(key);
    setError(null);
    try {
      await fn();
      router.refresh();
    } catch (err) {
      setError(err instanceof ApiCallError ? err.message : "Something failed.");
    } finally {
      setBusy(null);
    }
  }

  const createTeam = () =>
    run("team", async () => {
      await api(`/api/tournaments/${tournamentId}/teams`, {
        method: "POST",
        json: {
          name: teamName.trim(),
          captainUserId: Number(captainUserId),
          logoUrl: null,
        },
      });
      setTeamName("");
      setCaptainUserId("");
    });

  return (
    <div className="border border-line bg-panel">
      <div className="flex items-center gap-3 border-b border-line-soft px-5 py-3">
        <span aria-hidden className="block size-1.5 bg-accent" />
        <Eyebrow>Admin controls</Eyebrow>
      </div>

      <div className="grid gap-px bg-line lg:grid-cols-3">
        {/* 1. Registration */}
        <section className="bg-panel p-5">
          <p className="font-display text-sm uppercase tracking-wider text-muted">
            01 · Registration
          </p>
          <p className="tabular mt-2 text-sm text-dim">
            <span className="text-bone">{approvedCount}</span> approved players
          </p>
          <div className="mt-4 flex flex-wrap gap-2">
            {(["REGISTRATION", "DRAFTING", "LIVE"] as const).map((status) => (
              <button
                key={status}
                onClick={() =>
                  run(status, () =>
                    api(`/api/tournaments/${tournamentId}/status`, {
                      method: "PUT",
                      json: { status },
                    }),
                  )
                }
                disabled={busy !== null || tournamentStatus === status}
                className="border border-line px-3 py-1.5 font-display text-xs font-semibold uppercase tracking-wider text-muted transition-colors hover:border-dim hover:text-bone disabled:opacity-30"
              >
                {status === "REGISTRATION"
                  ? "Open sign-ups"
                  : status === "DRAFTING"
                    ? "Close sign-ups"
                    : "Lock rosters"}
              </button>
            ))}
          </div>
        </section>

        {/* 2. Teams */}
        <section className="bg-panel p-5">
          <p className="font-display text-sm uppercase tracking-wider text-muted">
            02 · Teams
          </p>
          <p className="tabular mt-2 text-sm text-dim">
            <span className="text-bone">{teamCount}</span> created
          </p>
          <div className="mt-4 space-y-2">
            <input
              value={teamName}
              onChange={(e) => setTeamName(e.target.value)}
              placeholder="Team name"
              className="w-full border border-line bg-ink px-3 py-2 text-sm text-bone placeholder:text-dim focus:border-accent focus:outline-none"
            />
            <select
              value={captainUserId}
              onChange={(e) => setCaptainUserId(e.target.value)}
              aria-label="Captain"
              className="w-full border border-line bg-ink px-3 py-2 text-sm text-bone focus:border-accent focus:outline-none"
            >
              <option value="">Choose a captain…</option>
              {profiles.map((p) => (
                <option key={p.userId} value={p.userId}>
                  {p.username}
                </option>
              ))}
            </select>
            <button
              onClick={createTeam}
              disabled={busy !== null || !teamName.trim() || !captainUserId}
              className={buttonClass("default", "w-full")}
            >
              {busy === "team" ? "Creating…" : "Add team"}
            </button>
          </div>
        </section>

        {/* 3. Auction */}
        <section className="bg-panel p-5">
          <p className="font-display text-sm uppercase tracking-wider text-muted">
            03 · Draft
          </p>
          <p className="tabular mt-2 text-sm text-dim">
            {auction ? (
              <>
                <span className="text-bone">{auction.pendingLots}</span> players
                queued · {auction.status.toLowerCase()}
              </>
            ) : (
              "No auction yet"
            )}
          </p>
          <div className="mt-4 space-y-2">
            {!auction ? (
              <button
                onClick={() =>
                  run("create", () =>
                    api(`/api/auctions/tournaments/${tournamentId}`, {
                      method: "POST",
                    }),
                  )
                }
                disabled={busy !== null}
                className={buttonClass("primary", "w-full")}
              >
                {busy === "create" ? "Creating…" : "Create the auction"}
              </button>
            ) : (
              <>
                <button
                  onClick={() =>
                    run("queue", () =>
                      api(`/api/auctions/${auction.auctionId}/queue?shuffle=true`, {
                        method: "POST",
                      }),
                    )
                  }
                  disabled={busy !== null || auction.status !== "SETUP"}
                  className={buttonClass("default", "w-full")}
                >
                  {busy === "queue"
                    ? "Building…"
                    : `Shuffle ${approvedCount} into the queue`}
                </button>
                <p className="text-xs text-dim">
                  {auction.status === "SETUP"
                    ? "The queue can only be rebuilt before the auction starts."
                    : "Start, pause and nominate from the draft room."}
                </p>
              </>
            )}
          </div>
        </section>
      </div>

      {error ? (
        <p
          role="alert"
          className="border-t border-line-soft px-5 py-3 text-sm text-signal"
        >
          {error}
        </p>
      ) : null}
    </div>
  );
}
