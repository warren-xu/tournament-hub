"use client";

import Image from "next/image";
import { useEffect, useMemo, useState } from "react";
import { PlayerName } from "./player-name";
import { RankBadge, Tag } from "@/components/ui";
import { api, ApiCallError } from "@/lib/client-api";
import { playLockIn, useSoundsEnabled } from "@/lib/sounds";
import { CORE_ROLES, rankCandidates, roleCoverage, type DraftPlayer } from "@/lib/draft-advice";
import type { AuctionSnapshot, AuctionTeamView, LotView, ProfileView, RankView } from "@/lib/types";
import type { RoleInfo } from "@/lib/valorant-roles";

/**
 * "Who should I go for?" for one captain: everyone still up for grabs, the player on the
 * block included, ranked for this team by lib/draft-advice. On the captain's nominating
 * turn each row also gets a button to nominate them.
 */
export function DraftAdvice({
  snapshot,
  team,
  profiles,
  ranks,
  roles,
  onSnapshot,
}: {
  snapshot: AuctionSnapshot;
  team: AuctionTeamView;
  profiles: Map<number, ProfileView>;
  ranks: Map<string, RankView>;
  /** Riot's description of each role, for the hover popups. */
  roles: Record<string, RoleInfo>;
  /** Applies the room state returned by a nomination. */
  onSnapshot: (snapshot: AuctionSnapshot) => void;
}) {
  const [queue, setQueue] = useState<LotView[]>([]);
  /** The needed role being hovered or focused, whose description opens under the list. */
  const [activeRole, setActiveRole] = useState<string | null>(null);
  // Kept after the pointer leaves, so the text doesn't vanish while the panel folds away.
  const [shownRole, setShownRole] = useState<string | null>(null);
  const showRole = (role: string | null) => {
    setActiveRole(role);
    if (role) setShownRole(role);
  };
  const shown = shownRole ? roles[shownRole] : undefined;
  const auctionId = snapshot.auctionId;
  const lot = snapshot.currentLot;
  const lotOpen = lot !== null;
  // The captain's turn to nominate: between rounds, while the draft is running.
  const myTurn = snapshot.status === "LIVE" && !lotOpen && snapshot.turnTeamId === team.teamId;
  const [picking, setPicking] = useState<number | null>(null);
  const soundOn = useSoundsEnabled();
  const [pickError, setPickError] = useState<string | null>(null);

  async function nominate(profileId: number) {
    setPicking(profileId);
    setPickError(null);
    try {
      onSnapshot(await api<AuctionSnapshot>(`/api/auctions/${auctionId}/pick`, {
        method: "POST",
        json: { playerProfileId: profileId },
      }));
      if (soundOn) playLockIn();
    } catch (err) {
      setPickError(err instanceof ApiCallError ? err.message : "Couldn't nominate that player.");
    } finally {
      setPicking(null);
    }
  }

  // The queue changes whenever a lot opens or closes.
  useEffect(() => {
    let live = true;
    api<LotView[]>(`/api/auctions/${auctionId}/queue`)
      .then((lots) => { if (live) setQueue(lots); })
      .catch(() => { if (live) setQueue([]); });
    return () => { live = false; };
  }, [auctionId, snapshot.pendingLots, lotOpen]);

  const { advice, missing } = useMemo(() => {
    const tierOf = (rank: string | null) => (rank ? ranks.get(rank.toLowerCase())?.tier ?? null : null);
    // The lot summaries carry rank and roles; the full profile is fresher if we have it.
    const asPlayer = (p: LotView["player"]): DraftPlayer => {
      const full = profiles.get(p.profileId);
      return {
        profileId: p.profileId,
        username: p.username,
        currentRank: full?.currentRank ?? p.currentRank,
        primaryRole: full?.primaryRole ?? p.primaryRole,
        secondaryRole: full?.secondaryRole ?? p.secondaryRole,
      };
    };
    const candidates = [...(lot && lot.status === "OPEN" ? [lot.player] : []), ...queue.map((l) => l.player)]
      .filter((p, i, all) => all.findIndex((q) => q.profileId === p.profileId) === i)
      .map(asPlayer);
    const roster: DraftPlayer[] = team.roster.map((entry) => {
      const full = profiles.get(entry.profileId);
      return {
        profileId: entry.profileId,
        username: entry.username,
        currentRank: full?.currentRank ?? null,
        primaryRole: full?.primaryRole ?? null,
        secondaryRole: full?.secondaryRole ?? null,
      };
    });
    const coverage = roleCoverage(roster);
    return {
      advice: rankCandidates(candidates, roster, team.rosterSize - team.rosterCount, tierOf),
      missing: CORE_ROLES.filter((r) => (coverage.get(r) ?? 0) < 1),
    };
  }, [lot, queue, team, profiles, ranks]);

  if (team.rosterCount >= team.rosterSize) return null;

  return (
    <section aria-labelledby="advice-heading" className="border border-line-soft bg-panel">
      <div className="border-b border-line-soft px-5 py-4">
        <h2 id="advice-heading" className="font-display text-base uppercase tracking-wide">
          Best available for {team.name}
        </h2>
        <p className="mt-1 text-xs leading-relaxed text-muted">
          {myTurn ? <><span className="text-bone">Your turn to nominate.</span> </> : null}
          Ranked by skill first, then by the roles you still need{missing.length === 0 ? " (every role is covered)" : ""}.
        </p>
        {missing.length > 0 ? (
          <>
            <ul className="mt-3 flex flex-wrap gap-1.5" aria-label="Roles you still need">
              {missing.map((role) => (
                <li key={role}>
                  <button type="button" className="role-chip" aria-describedby={roles[role] ? "role-detail" : undefined}
                    data-active={activeRole === role}
                    onMouseEnter={() => showRole(role)} onMouseLeave={() => showRole(null)}
                    onFocus={() => showRole(role)} onBlur={() => showRole(null)}>
                    {roles[role]?.iconUrl ? <Image src={roles[role].iconUrl} alt="" width={14} height={14} unoptimized className="size-3.5" /> : null}
                    {role}
                  </button>
                </li>
              ))}
            </ul>
            {/* Riot's description of the hovered role unfolds here, under the list. */}
            <div className="role-detail" data-open={activeRole !== null && roles[activeRole] !== undefined}>
              <div>
                <p id="role-detail" className="pt-3 text-xs leading-relaxed text-muted">
                  {shown ? <><span className="font-display font-semibold uppercase tracking-widest text-bone">{shownRole}</span> · {shown.description}</> : null}
                </p>
              </div>
            </div>
          </>
        ) : null}
      </div>
      {advice.length === 0 ? (
        <p className="px-5 py-4 text-sm text-muted">Nobody left to draft.</p>
      ) : (
        // Everyone left is listed, so captains can scout the whole queue between their turns.
        <ol className="max-h-96 divide-y divide-line-soft overflow-y-auto">
          {advice.map((a, i) => {
            const onBlock = lot?.status === "OPEN" && lot.player.profileId === a.player.profileId;
            const picked = snapshot.pickedPlayer?.profileId === a.player.profileId;
            return (
              <li key={a.player.profileId} className={`flex items-center gap-3 px-5 py-3 ${onBlock ? "bg-raise" : ""}`}>
                <span className="tabular w-5 shrink-0 font-display text-lg text-dim">{i + 1}</span>
                <div className="min-w-0 flex-1">
                  <p className="flex flex-wrap items-center gap-2">
                    <span className="truncate text-sm text-bone">
                      <PlayerName profileId={a.player.profileId}>{a.player.username}</PlayerName>
                    </span>
                    {onBlock ? <Tag tone="accent">Currently up for grabs</Tag> : null}
                    {picked ? <Tag tone="accent">{myTurn ? "Your pick" : "Nominated"}</Tag> : null}
                  </p>
                  <p className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted">
                    <RankBadge name={a.player.currentRank} ranks={ranks} size={18} />
                    {a.fills ? (
                      <span className="text-signal">
                        fills {a.fills}{a.fillsWith === "secondary" ? " (secondary)" : ""}
                      </span>
                    ) : a.player.primaryRole ? <span>{a.player.primaryRole}</span> : null}
                  </p>
                </div>
                {myTurn ? (
                  <button type="button" onClick={() => void nominate(a.player.profileId)}
                    disabled={picking !== null || picked}
                    className="nominate-btn" data-picked={picked || undefined}>
                    {picking === a.player.profileId ? "…" : picked ? "Picked" : "Nominate"}
                  </button>
                ) : null}
              </li>
            );
          })}
        </ol>
      )}
      {pickError ? <p role="alert" className="border-t border-line-soft px-5 py-3 text-sm text-signal">{pickError}</p> : null}
    </section>
  );
}
