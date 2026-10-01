"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Avatar, buttonClass, Tag } from "@/components/ui";
import { PlayerDialog } from "@/components/player-dialog";
import { api, ApiCallError } from "@/lib/client-api";
import type { AgentView, ProfileView, RankView } from "@/lib/types";

type Place = "captain" | "drafted" | "queued" | "pool";
const ORDER: Record<Place, number> = { captain: 0, drafted: 1, queued: 2, pool: 3 };

/**
 * The draft pool: everyone who signed up (the auction queue). Admins are also given every
 * other player, so they can tick them and add them in, or take anyone back out, until
 * the draft starts. The queue itself always runs highest rank first.
 */
export function DraftPool({
  tournamentId,
  profiles,
  queuedIds,
  captainUserIds,
  onTeamIds,
  isAdmin,
  editable,
  agents,
  ranks,
}: {
  tournamentId: number;
  profiles: ProfileView[];
  queuedIds: number[];
  captainUserIds: number[];
  /** Profiles on a roster: the captains, plus whoever has been drafted. */
  onTeamIds: number[];
  isAdmin: boolean;
  /** Before the draft starts; afterwards the queue is locked. */
  editable: boolean;
  agents: AgentView[];
  ranks: RankView[];
}) {
  const router = useRouter();
  const [selected, setSelected] = useState<number[]>([]);
  const [query, setQuery] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [opened, setOpened] = useState<ProfileView | null>(null);

  const placeOf = (p: ProfileView): Place =>
    captainUserIds.includes(p.userId) ? "captain"
      : onTeamIds.includes(p.id) ? "drafted"
        : queuedIds.includes(p.id) ? "queued" : "pool";
  const rows = profiles
    .filter((p) => `${p.username} ${p.riotId ?? ""} ${p.currentRank ?? ""}`.toLowerCase().includes(query.toLowerCase()))
    .sort((a, b) => ORDER[placeOf(a)] - ORDER[placeOf(b)] || a.username.localeCompare(b.username));
  const addable = profiles.filter((p) => placeOf(p) === "pool").map((p) => p.id);
  const picked = selected.filter((id) => addable.includes(id));
  const manage = isAdmin && editable;

  async function run(key: string, fn: () => Promise<unknown>) {
    setBusy(key);
    setError(null);
    try {
      await fn();
      router.refresh();
    } catch (err) {
      setError(err instanceof ApiCallError ? err.message : "Something went wrong.");
    } finally {
      setBusy(null);
    }
  }

  const addToQueue = () =>
    run("add", async () => {
      await api(`/api/tournaments/${tournamentId}/queue`, { method: "POST", json: { playerProfileIds: picked } });
      setSelected([]);
    });

  return (
    <div className="border border-line bg-panel">
      <div className="flex flex-wrap items-center gap-3 border-b border-line-soft px-4 py-3">
        <input type="search" value={query} onChange={(e) => setQuery(e.target.value)} aria-label="Find a player"
          placeholder="Search name, Riot ID or rank"
          className="min-w-48 flex-1 border border-line bg-ink px-3 py-2 text-sm text-bone placeholder:text-dim focus:border-accent focus:outline-none" />
        {manage ? (
          <>
            <button type="button" className={buttonClass("ghost")} disabled={busy !== null || addable.length === 0}
              onClick={() => setSelected(picked.length === addable.length ? [] : addable)}>
              {picked.length === addable.length && addable.length > 0 ? "Clear selection" : `Select all ${addable.length} not queued`}
            </button>
            <button type="button" className={buttonClass("primary")} disabled={busy !== null || picked.length === 0} onClick={addToQueue}>
              {busy === "add" ? "Adding…" : picked.length ? `Add ${picked.length} to the queue` : "Add to the queue"}
            </button>
          </>
        ) : null}
      </div>

      {error ? <p role="alert" className="border-b border-line-soft px-4 py-3 text-sm text-signal">{error}</p> : null}

      {rows.length === 0 ? (
        <p className="px-4 py-6 text-sm text-muted">No players match your search.</p>
      ) : (
        <ul className="max-h-[32rem] divide-y divide-line-soft overflow-y-auto">
          {rows.map((p) => {
            const place = placeOf(p);
            const tickable = manage && place === "pool";
            const body = (
              <>
                <Avatar src={p.avatarUrl} name={p.username} />
                <span className="min-w-0 flex-1">
                  {/* A button inside the row's label opens the card without ticking the checkbox. */}
                  <button type="button" onClick={() => setOpened(p)}
                    aria-haspopup="dialog"
                    className="block min-h-0 max-w-full cursor-pointer truncate text-left text-sm text-bone underline-offset-4 hover:text-white hover:underline focus-visible:underline">
                    {p.username}
                  </button>
                  <span className="block truncate text-xs text-dim">
                    {[p.currentRank ?? "No rank set", p.primaryRole].filter(Boolean).join(" · ")}
                  </span>
                </span>
              </>
            );
            return (
              <li key={p.id} className="flex items-center gap-3 px-4 py-2.5">
                {tickable ? (
                  <label className="flex min-w-0 flex-1 cursor-pointer items-center gap-3">
                    <input type="checkbox" className="size-4 accent-accent" checked={picked.includes(p.id)}
                      onChange={(e) => setSelected(e.target.checked ? [...selected, p.id] : selected.filter((id) => id !== p.id))} />
                    {body}
                  </label>
                ) : (
                  <span className="flex min-w-0 flex-1 items-center gap-3">{body}</span>
                )}
                {place === "captain" ? <Tag tone="outline">Captain</Tag> : null}
                {place === "drafted" ? <Tag tone="outline">Drafted</Tag> : null}
                {place === "queued" ? <Tag tone="accent">In queue</Tag> : null}
                {manage && place === "queued" ? (
                  <button type="button" disabled={busy !== null}
                    onClick={() => run(`remove-${p.id}`, () => api(`/api/tournaments/${tournamentId}/queue/${p.id}`, { method: "DELETE" }))}
                    className="border border-line px-3 py-1 font-display text-xs font-semibold uppercase tracking-wider text-muted transition-colors hover:border-accent-deep hover:text-signal disabled:opacity-30">
                    {busy === `remove-${p.id}` ? "Removing…" : "Remove"}
                  </button>
                ) : null}
              </li>
            );
          })}
        </ul>
      )}

      <PlayerDialog profile={opened} agents={agents} ranks={ranks} onClose={() => setOpened(null)} />
    </div>
  );
}
