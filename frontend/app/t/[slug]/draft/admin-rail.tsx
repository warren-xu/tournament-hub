"use client";

import { useCallback, useEffect, useState } from "react";
import { api, ApiCallError } from "@/lib/client-api";
import type { AuctionSnapshot, LotView } from "@/lib/types";

/**
 * The admin's controls, sitting beside the room rather than on a separate page:
 * nominating is a reaction to what just happened, so it needs the room in view.
 */
export function AdminRail({
  snapshot,
  onSnapshot,
}: {
  snapshot: AuctionSnapshot;
  onSnapshot: (snapshot: AuctionSnapshot) => void;
}) {
  const [queue, setQueue] = useState<LotView[]>([]);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const auctionId = snapshot.auctionId;
  const lotOpen = snapshot.currentLot !== null;
  const turnTeam = snapshot.teams.find((t) => t.teamId === snapshot.turnTeamId);

  // The random fill runs by itself as soon as the last credit is spent. The button is
  // here for the case an undo puts credits back and the draw has to be re-run.
  const floor = snapshot.currentLot?.minBid ?? 1;
  const nobodyCanBid =
    snapshot.teams.length > 0 && snapshot.teams.every((team) => team.maxBid < floor);
  const slotsLeft = snapshot.teams.some((team) => team.rosterCount < team.rosterSize);

  const loadQueue = useCallback(() => {
    api<LotView[]>(`/api/auctions/${auctionId}/queue`)
      .then(setQueue)
      .catch(() => setQueue([]));
  }, [auctionId]);

  // The queue changes whenever a lot opens or closes.
  useEffect(loadQueue, [loadQueue, snapshot.pendingLots, lotOpen]);

  async function run(key: string, path: string, init?: RequestInit & { json?: unknown }) {
    setBusy(key);
    setError(null);
    try {
      onSnapshot(
        await api<AuctionSnapshot>(path, { method: "POST", ...init }),
      );
    } catch (err) {
      setError(err instanceof ApiCallError ? err.message : "Request failed.");
    } finally {
      setBusy(null);
    }
  }

  const nominate = (playerProfileId: number | null) =>
    run("nominate", `/api/auctions/${auctionId}/nominate`, {
      json: { playerProfileId },
    });

  return (
    <div className="bg-panel">
      <div className="flex items-center gap-2 border-b border-line-soft px-5 py-3">
        <span aria-hidden className="block size-1.5 bg-accent" />
        <p className="eyebrow">Admin</p>
      </div>

      <div className="space-y-4 p-5">
        <p className="text-sm leading-relaxed text-muted">
          {snapshot.status === "COMPLETE" ? "Draft finished. Undo only to fix a pick."
            : snapshot.status === "SETUP" ? "Start the draft, then nominate a player."
            : snapshot.status === "PAUSED" ? "Paused. Resume to continue."
            : lotOpen ? "Round open. Results reveal automatically, or close it early."
            : snapshot.pickedPlayer ? `${turnTeam?.name ?? "The captain"} nominated ${snapshot.pickedPlayer.username}. Open bidding when you're ready.`
            : turnTeam ? `Waiting for ${turnTeam.name} to pick. You can nominate someone yourself if they're away.`
            : "Nominate the next player."}
        </p>
        <div className="flex flex-wrap gap-2">
          {snapshot.status === "SETUP" ? (
            <Control
              label="Start"
              disabled={busy !== null}
              busy={busy === "start"}
              onClick={() => run("start", `/api/auctions/${auctionId}/start`)}
              primary
            />
          ) : null}

          {snapshot.status === "LIVE" ? (
            <Control
              label="Pause"
              disabled={busy !== null}
              busy={busy === "pause"}
              onClick={() => run("pause", `/api/auctions/${auctionId}/pause`)}
            />
          ) : null}

          {snapshot.status === "PAUSED" ? (
            <Control
              label="Resume"
              disabled={busy !== null}
              busy={busy === "resume"}
              onClick={() => run("resume", `/api/auctions/${auctionId}/resume`)}
              primary
            />
          ) : null}

          {snapshot.status === "LIVE" && !lotOpen && nobodyCanBid && slotsLeft ? (
            <Control
              label="Assign rest randomly"
              disabled={busy !== null}
              busy={busy === "fill"}
              onClick={() => run("fill", `/api/auctions/${auctionId}/fill-random`)}
              primary
            />
          ) : null}

          {snapshot.status === "LIVE" && !lotOpen ? (
            <Control
              label="End draft"
              disabled={busy !== null}
              busy={busy === "complete"}
              onClick={() => run("complete", `/api/auctions/${auctionId}/complete`)}
            />
          ) : null}

          <Control
            label="Undo last sale"
            disabled={busy !== null}
            busy={busy === "undo"}
            onClick={() => run("undo", `/api/auctions/${auctionId}/undo`)}
          />

          {lotOpen ? (
            <Control
              label="Close round"
              disabled={busy !== null}
              busy={busy === "close"}
              onClick={() =>
                run(
                  "close",
                  `/api/auctions/${auctionId}/lots/${snapshot.currentLot!.lotId}/close`,
                )
              }
            />
          ) : null}
        </div>

        <div>
          <button
            onClick={() => nominate(null)}
            disabled={busy !== null || snapshot.status !== "LIVE" || lotOpen || snapshot.pendingLots === 0}
            className="corner-cut-sm w-full bg-accent px-4 py-2.5 font-display text-sm font-semibold uppercase tracking-wider text-white transition-colors hover:bg-accent-deep disabled:cursor-not-allowed disabled:bg-raise disabled:text-dim"
          >
            {busy === "nominate"
              ? "Nominating…"
              : lotOpen
                ? "Round in progress"
                : snapshot.pickedPlayer
                  ? `Open bidding on ${snapshot.pickedPlayer.username}`
                  : "Nominate next in queue (no pick yet)"}
          </button>
        </div>

        {queue.length > 0 ? (
          <div>
            <p className="eyebrow mb-2">Queue · {queue.length}</p>
            <ul className="max-h-56 overflow-y-auto border border-line-soft">
              {queue.map((lot) => (
                <li key={lot.lotId}>
                  <button
                    onClick={() => nominate(lot.player.profileId)}
                    disabled={busy !== null || snapshot.status !== "LIVE" || lotOpen}
                    className="flex w-full items-baseline justify-between gap-3 border-b border-line-soft px-3 py-2 text-left text-sm transition-colors hover:bg-raise disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    <span className="truncate text-bone">
                      {lot.player.username}
                    </span>
                    <span className="shrink-0 text-xs text-dim">
                      {/* Nobody bid the first time round; they are still placeable. */}
                      {lot.status === "UNSOLD"
                        ? "unsold"
                        : (lot.player.currentRank ?? lot.player.primaryRole ?? "—")}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          </div>
        ) : null}

        {error ? (
          <p role="alert" className="text-sm text-signal">
            {error}
          </p>
        ) : null}
      </div>
    </div>
  );
}

function Control({
  label,
  onClick,
  busy,
  primary = false,
  disabled = false,
}: {
  label: string;
  onClick: () => void;
  busy: boolean;
  primary?: boolean;
  disabled?: boolean;
}) {
  return (
    <button
      onClick={onClick}
      disabled={busy || disabled}
      className={`px-3 py-1.5 font-display text-xs font-semibold uppercase tracking-wider transition-colors disabled:opacity-40 ${
        primary
          ? "bg-accent text-white hover:bg-accent-deep"
          : "border border-line text-muted hover:border-dim hover:text-bone"
      }`}
    >
      {busy ? "…" : label}
    </button>
  );
}
