"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { buttonClass, Eyebrow } from "@/components/ui";
import { LocalTime } from "@/components/local-time";
import { api, ApiCallError } from "@/lib/client-api";
import type {
  AuctionSnapshot,
  ProfileView,
  TournamentStatus,
} from "@/lib/types";

/** "2026-10-02T23:30:00Z" → the "YYYY-MM-DDTHH:mm" a datetime-local input wants, in local time. */
function toLocalInput(iso: string | null): string {
  if (!iso) return "";
  const d = new Date(iso);
  return new Date(d.getTime() - d.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
}

/** The team size the draft will use if it starts now: everyone split across the teams, rounded up. */
function splitCopy(teams: number, seated: number, queued: number): string {
  if (teams < 2) return "Add at least two teams.";
  const people = seated + queued;
  const size = Math.ceil(people / teams);
  const short = size * teams - people;
  return short === 0
    ? `Teams of ${size}, captains included.`
    : `Teams of ${size}, captains included; ${short} ${short === 1 ? "team" : "teams"} a player short.`;
}

/**
 * Everything an admin does before the draft room takes over: open sign-ups, set the
 * date, create the captains' teams. The queue itself is managed from the draft pool.
 */
export function AuctionSetup({
  tournamentId,
  tournamentStatus,
  startsAt,
  auction,
  approvedCount,
  teamCount,
  seatedCount,
}: {
  tournamentId: number;
  tournamentStatus: TournamentStatus;
  startsAt: string | null;
  auction: AuctionSnapshot | null;
  approvedCount: number;
  teamCount: number;
  /** People already on a team (the captains, before the draft). */
  seatedCount: number;
}) {
  const router = useRouter();
  const [profiles, setProfiles] = useState<ProfileView[]>([]);
  const [teamName, setTeamName] = useState("");
  const [captainUserId, setCaptainUserId] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  // Only filled in once opened: the input needs the browser's time zone, which the server can't know.
  const [schedule, setSchedule] = useState<string | null>(null);
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

  const saveSchedule = (value: string) =>
    run("schedule", async () => {
      await api(`/api/tournaments/${tournamentId}/schedule`, {
        method: "PUT",
        json: { startsAt: value ? new Date(value).toISOString() : null },
      });
      setSchedule(null);
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
            <span className="text-bone">{approvedCount}</span> in the queue
          </p>
          <div className="mt-3 text-sm">
            {schedule === null ? (
              <p className="flex flex-wrap items-baseline gap-x-3 gap-y-1 text-dim">
                <span>
                  Starts{" "}
                  <span className="text-bone">{startsAt ? <LocalTime iso={startsAt} /> : "TBA"}</span>
                </span>
                <button type="button" onClick={() => setSchedule(toLocalInput(startsAt))}
                  className="text-xs text-muted underline underline-offset-4 hover:text-bone">
                  {startsAt ? "Change" : "Set a date"}
                </button>
              </p>
            ) : (
              <form className="space-y-2" onSubmit={(e) => { e.preventDefault(); saveSchedule(schedule); }}>
                <label className="block text-xs text-muted">
                  Start date and time (your time zone)
                  <input type="datetime-local" value={schedule} onChange={(e) => setSchedule(e.target.value)}
                    className="mt-1 w-full border border-line bg-ink px-3 py-2 text-sm text-bone focus:border-accent focus:outline-none" />
                </label>
                <div className="flex flex-wrap gap-2">
                  <button type="submit" disabled={busy !== null} className={buttonClass("default")}>
                    {busy === "schedule" ? "Saving…" : "Save"}
                  </button>
                  {startsAt ? (
                    <button type="button" disabled={busy !== null} onClick={() => saveSchedule("")} className={buttonClass("ghost")}>
                      Clear date
                    </button>
                  ) : null}
                  <button type="button" onClick={() => setSchedule(null)} className={buttonClass("ghost")}>Cancel</button>
                </div>
              </form>
            )}
          </div>
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
          <p className="mt-1 text-xs text-dim">Each captain leaves the queue and leads their team.</p>
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
                {auction.status === "SETUP" ? (
                  <span className="mt-1 block text-xs">{splitCopy(teamCount, seatedCount, auction.pendingLots)}</span>
                ) : null}
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
              <p className="text-xs text-dim">
                {auction.status === "SETUP"
                  ? "The queue runs highest rank first. Sign-ups join it on their own; add anyone else from the draft pool below. Team size is set when you start the draft."
                  : "Start, pause and nominate from the draft room."}
              </p>
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
