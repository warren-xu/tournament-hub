"use client";

import Image from "next/image";
import { useEffect, useMemo, useRef, useState, type RefObject } from "react";
import { AdminRail } from "./admin-rail";
import { DraftAdvice } from "./draft-advice";
import { PlayerName, ProfileOpener } from "./player-name";
import { PlayerDialog } from "@/components/player-dialog";
import { PlayerCard } from "@/components/player-card";
import { asAuctionProfile } from "@/lib/nerfs";
import { NerfLabel } from "@/components/nerf-label";
import { Avatar, NO_FORM_RESTORE, RankBadge, rankIndex, Tag } from "@/components/ui";
import {
  useAuction,
  type ConnectionState,
  type LastResult,
} from "@/lib/use-auction";
import { playBidLevel, playRoundStart, playTick, playTimeUp, playTurnChime, playWon, setSoundsEnabled, unlockAudio, useSoundsEnabled } from "@/lib/sounds";
import type { RoleInfo } from "@/lib/valorant-roles";
import type {
  AgentView,
  AuctionSnapshot,
  AuctionTeamView,
  Me,
  PlayerSummary,
  ProfileView,
  RankView,
} from "@/lib/types";

/** How long each result stays up before the next captain can nominate. */
const RESULT_PAUSE_MS = 5000;

export function AuctionRoom({
  initial,
  me,
  ranks,
  profiles,
  agents,
  roles,
  creditBudget,
}: {
  initial: AuctionSnapshot;
  me: Me | null;
  ranks: RankView[];
  profiles: ProfileView[];
  agents: AgentView[];
  roles: Record<string, RoleInfo>;
  creditBudget: number;
}) {
  const {
    snapshot,
    connection,
    rejection,
    lastResult,
    submitBid,
    applySnapshot,
    clockOffset,
  } = useAuction(initial.auctionId, initial);

  const rankLookup = rankIndex(ranks);
  // Everything in the room sees players as the auction does (roles adjusted for nerfs).
  const profileLookup = useMemo(
    () => new Map(profiles.map((p) => [p.id, asAuctionProfile(p)])),
    [profiles],
  );
  const [openProfileId, setOpenProfileId] = useState<number | null>(null);

  const opener = useMemo(() => ({
    open: setOpenProfileId,
    has: (id: number) => profileLookup.has(id),
  }), [profileLookup]);
  const lot = snapshot.currentLot;
  const myTeam = me
    ? snapshot.teams.find((t) => t.captainUserId === me.userId)
    : undefined;
  // After each result the room holds for a moment before the next nomination, so people
  // can take in who went where. Starts only for results that land while you're here.
  const resultLotId = lastResult?.lot.lotId ?? null;
  // The last result whose pause has run out; any newer one is still being held.
  const [releasedLotId, setReleasedLotId] = useState<number | null>(null);
  const holding = resultLotId !== null && resultLotId !== releasedLotId;
  useEffect(() => {
    if (resultLotId === null) return;
    const timer = setTimeout(() => setReleasedLotId(resultLotId), RESULT_PAUSE_MS);
    return () => clearTimeout(timer);
  }, [resultLotId]);
  // A captain's nominating turn: between rounds, while the draft runs, once the pause is over.
  const myTurn = myTeam !== undefined && snapshot.status === "LIVE" && !lot
    && snapshot.turnTeamId === myTeam.teamId && !holding;
  const soundOn = useSoundsEnabled();
  // Bidding opening: a new round starting while you're here, not one already running at load.
  const openLotId = lot?.status === "OPEN" ? lot.lotId : null;
  const lastOpenLot = useRef(openLotId);
  useEffect(() => {
    if (openLotId !== null && openLotId !== lastOpenLot.current && soundOn) playRoundStart();
    lastOpenLot.current = openLotId;
  }, [openLotId, soundOn]);

  // Every bid on the player up plays the next level for the whole room. Counted from what
  // the room has seen, so loading or resyncing mid-lot doesn't replay anything.
  const bidCount = lot?.status === "OPEN" ? snapshot.recentBids.length : 0;
  const lastBids = useRef<{ lotId: number | null; count: number }>({ lotId: openLotId, count: bidCount });
  useEffect(() => {
    const seen = lastBids.current;
    if (openLotId !== null && openLotId === seen.lotId && bidCount > seen.count && soundOn) {
      playBidLevel(bidCount);
    }
    lastBids.current = { lotId: openLotId, count: bidCount };
  }, [openLotId, bidCount, soundOn]);

  // A player settled while you're here: the captain who got them hears the win, everyone
  // else the time-up. Early closes count too, and nothing replays on load.
  const lastResultLot = useRef(resultLotId);
  useEffect(() => {
    if (resultLotId !== null && resultLotId !== lastResultLot.current && soundOn && lastResult) {
      const mine = myTeam !== undefined && lastResult.lot.status === "SOLD"
        && lastResult.lot.winningTeamId === myTeam.teamId;
      if (mine) playWon();
      else playTimeUp();
    }
    lastResultLot.current = resultLotId;
  }, [resultLotId, lastResult, myTeam, soundOn]);

  // Browsers only allow audio after an interaction; the first one anywhere on the page
  // unlocks it for the whole visit, so the countdown and your-turn cues can play later.
  useEffect(() => {
    const unlock = () => {
      unlockAudio();
      window.removeEventListener("pointerdown", unlock);
      window.removeEventListener("keydown", unlock);
    };
    window.addEventListener("pointerdown", unlock);
    window.addEventListener("keydown", unlock);
    return () => {
      window.removeEventListener("pointerdown", unlock);
      window.removeEventListener("keydown", unlock);
    };
  }, []);
  const [turnToast, setTurnToast] = useState(false);
  const wasMyTurn = useRef(myTurn);
  useEffect(() => {
    // Only a turn that starts while you're here: not one already running when the page loads.
    if (myTurn && !wasMyTurn.current) {
      if (soundOn) playTurnChime();
      setTurnToast(true);
    }
    wasMyTurn.current = myTurn;
  }, [myTurn, soundOn]);
  useEffect(() => {
    if (!turnToast) return;
    const timer = setTimeout(() => setTurnToast(false), 6000);
    return () => clearTimeout(timer);
  }, [turnToast]);
  // Visible from another tab: the title carries the cue while it's your turn. Next.js
  // rewrites the title as the page settles, so the prefix is re-applied whenever it does.
  useEffect(() => {
    if (!myTurn) return;
    const prefix = "● Your turn · ";
    const apply = () => {
      if (!document.title.startsWith(prefix)) document.title = prefix + document.title;
    };
    apply();
    const observer = new MutationObserver(apply);
    observer.observe(document.head, { childList: true, subtree: true, characterData: true });
    return () => {
      observer.disconnect();
      if (document.title.startsWith(prefix)) document.title = document.title.slice(prefix.length);
    };
  }, [myTurn]);

  return (
    <ProfileOpener.Provider value={opener}>
    <PlayerDialog profile={openProfileId === null ? null : profileLookup.get(openProfileId) ?? null}
      agents={agents} ranks={ranks} onClose={() => setOpenProfileId(null)} />
    {/* Gone after six seconds, when dismissed, or as soon as the turn moves on. */}
    {turnToast && myTurn ? (
      <div role="alert" className="turn-toast">
        <span className="turn-dot" aria-hidden />
        <span>
          <span className="block font-display text-lg uppercase tracking-wide">Your turn</span>
          <span className="block text-xs text-bone/80">Nominate a player from Best available.</span>
        </span>
        <button type="button" className="turn-toast-close" aria-label="Dismiss" onClick={() => setTurnToast(false)}>✕</button>
      </div>
    ) : null}
    <div className="auction-room space-y-5">
      <StatusBar
        soundOn={soundOn}
        onToggleSound={() => setSoundsEnabled(!soundOn)}
        connection={connection}
        snapshot={snapshot}
        pending={snapshot.pendingLots}
      />

      {snapshot.status === "COMPLETE" ? (
        <FinalRosters teams={snapshot.teams} profiles={profileLookup} />
      ) : (
      <div className="grid items-start gap-5 lg:grid-cols-[1.7fr_1fr]">
        <div className="space-y-5">
          {holding
            ? <UpNext snapshot={snapshot} myTeamId={myTeam?.teamId} />
            : <NominationStatus snapshot={snapshot} myTeamId={myTeam?.teamId} />}
          {/* On your turn the list you nominate from comes straight under the banner. */}
          {myTeam && myTurn ? (
            <DraftAdvice snapshot={snapshot} team={myTeam} profiles={profileLookup} ranks={rankLookup} roles={roles}
              onSnapshot={applySnapshot} holding={holding} />
          ) : null}
          <LotCard
            snapshot={snapshot}
            lastResult={lastResult}
            offsetRef={clockOffset}
            myTeamId={myTeam?.teamId}
            ranks={rankLookup}
            profiles={profileLookup}
            agents={agents}
          />
          {myTeam ? <BidControls
            key={lot?.lotId ?? "idle"}
            snapshot={snapshot}
            me={me}
            myTeam={myTeam}
            rejection={rejection?.message ?? null}
            // The room's bid sound plays when the bid lands, for everyone, this captain included.
            onBid={submitBid}
            offsetRef={clockOffset}
            connection={connection}
          /> : <p className="border-l-2 border-line bg-panel px-5 py-4 text-sm text-muted">Spectator view · Follow each pick and bid live. No sign-in needed.</p>}
          {myTeam && !myTurn ? (
            <DraftAdvice snapshot={snapshot} team={myTeam} profiles={profileLookup} ranks={rankLookup} roles={roles}
              onSnapshot={applySnapshot} holding={holding} />
          ) : null}
        </div>

        <div className="space-y-5">
          <TeamsRail
            teams={snapshot.teams}
            nominatingTeamId={!lot && (snapshot.status === "LIVE" || snapshot.status === "PAUSED") ? snapshot.turnTeamId : null}
            myTeamId={myTeam?.teamId}
            leadingTeamId={lot?.status === "OPEN" ? lot.winningTeamId : null}
            creditBudget={creditBudget}
            teamSize={snapshot.status === "SETUP" ? projectedTeamSize(snapshot) : null}
          />
          {me?.role === "ADMIN" ? (
            <AdminRail snapshot={snapshot} onSnapshot={applySnapshot} />
          ) : null}
        </div>
      </div>
      )}
    </div>
    </ProfileOpener.Provider>
  );
}

/**
 * Whose turn it is to nominate, and what they picked. Shown between rounds; once bidding
 * opens, the lot itself takes over.
 */
function NominationStatus({ snapshot, myTeamId }: {
  snapshot: AuctionSnapshot;
  myTeamId?: number;
}) {
  if (snapshot.currentLot || snapshot.turnTeamId === null
      || (snapshot.status !== "LIVE" && snapshot.status !== "PAUSED")) return null;
  const team = snapshot.teams.find((t) => t.teamId === snapshot.turnTeamId);
  if (!team) return null;
  const mine = team.teamId === myTeamId;
  const pick = snapshot.pickedPlayer;

  const choosing = !pick;

  return (
    // data-active runs the sweeping bar while someone is still choosing.
    <div role="status" className="turn-banner" data-mine={mine || undefined} data-active={choosing || undefined}>
      <span className="turn-dot" aria-hidden data-still={!choosing || undefined} />
      <div className="min-w-0 flex-1">
        {pick ? (
          <p className="text-sm text-muted">
            <span className="text-bone">{mine ? "You" : team.name}</span> nominated{" "}
            <PlayerName profileId={pick.profileId}><span className="text-bone">{pick.username}</span></PlayerName>.
            {" "}Waiting for the admin to open bidding{mine ? ". You can still change your pick below" : ""}.
          </p>
        ) : mine ? (
          <>
            <p className="font-display text-2xl uppercase leading-none tracking-wide text-bone">Your turn to nominate</p>
            <p className="mt-1.5 text-sm text-muted">Pick a player to nominate for bidding.</p>
          </>
        ) : (
          <p className="text-sm text-muted">
            <span className="font-display text-base uppercase tracking-wide text-bone">{team.name}</span> is choosing who to nominate.
          </p>
        )}
      </div>
    </div>
  );
}

/**
 * Shown during the pause after a result: who nominates next, with a spinner until their
 * turn opens. The captain who's up gets told directly.
 */
function UpNext({ snapshot, myTeamId }: { snapshot: AuctionSnapshot; myTeamId?: number }) {
  if (snapshot.status !== "LIVE" && snapshot.status !== "PAUSED") return null;
  const team = snapshot.teams.find((t) => t.teamId === snapshot.turnTeamId);
  if (!team) return null;
  const mine = team.teamId === myTeamId;
  return (
    <div className="turn-banner" data-mine={mine || undefined} role="status">
      <span className="up-next-spinner" aria-hidden />
      {mine ? (
        <p className="font-display text-2xl uppercase leading-none tracking-wide text-bone">You&rsquo;re up next!</p>
      ) : (
        <p className="text-sm text-muted">
          Up next for nominating: <span className="font-display text-base uppercase tracking-wide text-bone">{team.name}</span>
        </p>
      )}
    </div>
  );
}

/* ---------------------------------------------------------------- status bar */

const STATUS_COPY: Record<AuctionSnapshot["status"], string> = {
  SETUP: "Not started",
  LIVE: "Live",
  PAUSED: "Paused",
  COMPLETE: "Finished",
};

const CONNECTION_COPY: Record<ConnectionState, string> = {
  connecting: "Connecting",
  live: "Connected",
  reconnecting: "Reconnecting",
  offline: "Disconnected",
};

function StatusBar({
  soundOn,
  onToggleSound,
  connection,
  snapshot,
  pending,
}: {
  soundOn: boolean;
  onToggleSound: () => void;
  connection: ConnectionState;
  snapshot: AuctionSnapshot;
  pending: number;
}) {
  const running = snapshot.status === "LIVE";
  return (
    <div className="flex flex-wrap items-center gap-x-8 gap-y-2 bg-panel px-5 py-3">
      <div className="flex items-center gap-2.5">
        <span
          aria-hidden
          className={`block size-2 ${running ? "live-dot bg-accent" : "bg-dim"}`}
        />
        <span className="font-display text-sm font-semibold uppercase tracking-widest">
          {STATUS_COPY[snapshot.status]}
        </span>
      </div>

      <span className="tabular text-sm text-muted">
        <span className="text-bone">{pending}</span> in queue
      </span>

      <button type="button" className="turn-sound ml-auto" aria-pressed={soundOn} onClick={onToggleSound}>
        Sounds: {soundOn ? "on" : "off"}
      </button>

      <div className="flex items-center gap-2">
        <span
          aria-hidden
          className={`block size-1.5 ${
            connection === "live" ? "bg-muted" : "bg-accent"
          }`}
        />
        <span
          className={`font-display text-xs font-semibold uppercase tracking-widest ${
            connection === "live" ? "text-dim" : "text-signal"
          }`}
          role="status"
        >
          {CONNECTION_COPY[connection]}
        </span>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ lot card */

function LotCard({
  snapshot,
  lastResult,
  offsetRef,
  myTeamId,
  ranks,
  profiles,
  agents,
}: {
  snapshot: AuctionSnapshot;
  lastResult: LastResult | null;
  offsetRef: RefObject<number>;
  myTeamId?: number;
  ranks: Map<string, RankView>;
  profiles: Map<number, ProfileView>;
  agents: AgentView[];
}) {
  const lot = snapshot.currentLot;

  if (!lot) {
    // A captain's pick is the newest news, so it takes over from the last result.
    if (snapshot.pickedPlayer) {
      return (
        <NominatedCard
          player={snapshot.pickedPlayer}
          profile={profiles.get(snapshot.pickedPlayer.profileId)}
          teamName={snapshot.teams.find((t) => t.teamId === snapshot.turnTeamId)?.name ?? null}
          agents={agents}
          ranks={ranks}
        />
      );
    }
    // The last result stays up until the next player.
    if (lastResult) {
      return <ResultCard result={lastResult} myTeamId={myTeamId} ranks={ranks} />;
    }
    return (
      <div className="flex min-h-72 flex-col items-center justify-center bg-panel px-6 py-16 text-center">
        <p className="font-display text-2xl uppercase tracking-wide text-dim">
          {snapshot.pendingLots > 0 ? "Waiting for next player" : "No players left"}
        </p>
      </div>
    );
  }

  const iHold = myTeamId !== undefined && lot.winningTeamId === myTeamId;
  const bids = snapshot.recentBids;

  return (
    <div className="corner-cut bg-panel p-6 sm:p-8">
      <div className="flex flex-wrap items-start justify-between gap-6">
        <div className="flex items-start gap-4">
          <Avatar src={lot.player.avatarUrl} name={lot.player.username} size={64} />
          <div>
            <p className="eyebrow">Lot {lot.seq}</p>
            <h2 className="mt-1 text-4xl uppercase leading-none tracking-tight sm:text-5xl">
              <PlayerName profileId={lot.player.profileId}>{lot.player.username}</PlayerName>
            </h2>
            <p className="mt-2 font-mono text-sm text-dim">
              {lot.player.riotId ?? "—"}
            </p>
            <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1.5">
              <RankBadge name={lot.player.currentRank} ranks={ranks} size={24} />
              {lot.player.primaryRole ? (
                <Tag tone="outline">{lot.player.primaryRole}</Tag>
              ) : null}
              {lot.player.secondaryRole ? (
                <Tag tone="outline">{lot.player.secondaryRole}</Tag>
              ) : null}
            </div>
          </div>
        </div>

        <Countdown
          key={lot.lotId}
          endsAt={lot.endsAt}
          serverTime={snapshot.serverTime}
          offsetRef={offsetRef}
          paused={snapshot.status === "PAUSED"}
        />
      </div>

      <NomineeDetails profile={profiles.get(lot.player.profileId)} agents={agents} ranks={ranks} />

      <div className="mt-8 flex flex-wrap items-end justify-between gap-6 border-t border-line-soft pt-6">
        <div>
          <p className="eyebrow">{bids.length > 0 ? "Current bid" : "Starting at"}</p>
          <p className="tabular mt-1 font-display text-6xl font-semibold leading-none text-signal">
            {lot.winningBid}
          </p>
          <p className="mt-2 flex flex-wrap items-center gap-2 font-display text-base uppercase tracking-wide">
            {lot.winningTeamName
              ? <>{bids.length > 0 ? "Held by" : "Nominated by"} <span className="text-bone">{lot.winningTeamName}</span></>
              : <span className="text-dim">No bids yet</span>}
            {iHold ? <Tag tone="accent">You</Tag> : null}
          </p>
        </div>
        {bids.length > 0 ? (
          <ol aria-label="Bids so far" className="tabular min-w-48 space-y-1 text-sm">
            {bids.slice(0, 5).map((bid, index) => (
              <li key={bid.bidId} className={`flex justify-between gap-6 ${index === 0 ? "text-bone" : "text-dim"}`}>
                <span className="truncate">{bid.teamName ?? "A team"}{bid.teamId === myTeamId ? " (you)" : ""}</span>
                <span className="font-display font-semibold">{bid.amount}</span>
              </li>
            ))}
          </ol>
        ) : (
          <p className="max-w-60 text-sm text-muted">
            {lot.winningTeamName ? `${lot.winningTeamName} keeps them for 0 if nobody bids.` : "Nobody holds this player yet."}
          </p>
        )}
      </div>
    </div>
  );
}

/**
 * The rest of the nominee's profile: what a captain weighs before bidding. Rank and roles
 * are already in the header; this adds peak, main agent, agent pool and their own notes.
 */
function NomineeDetails({
  profile,
  agents,
  ranks,
}: {
  profile: ProfileView | undefined;
  agents: AgentView[];
  ranks: Map<string, RankView>;
}) {
  if (!profile) return null;
  const main = agents.find((a) => a.name.toLowerCase() === profile.mainAgent?.toLowerCase());
  const pool = profile.agents
    .map((name) => ({ name, agent: agents.find((a) => a.name === name) }))
    // The main agent is shown on its own, so it leads rather than repeats.
    .sort((a, b) => Number(b.name === profile.mainAgent) - Number(a.name === profile.mainAgent));

  return (
    <dl className="mt-6 grid gap-x-8 gap-y-5 border-t border-line-soft pt-6 sm:grid-cols-3">
      {profile.nerfTier ? (
        <div className="sm:col-span-3">
          <dt className="eyebrow">Nerf</dt>
          <dd className="mt-1.5"><NerfLabel tier={profile.nerfTier} /></dd>
        </div>
      ) : null}
      <div>
        <dt className="eyebrow">Peak rank</dt>
        <dd className="mt-1.5">
          {profile.peakRank ? <RankBadge name={profile.peakRank} ranks={ranks} size={22} /> : <span className="text-sm text-dim">—</span>}
        </dd>
      </div>
      <div>
        <dt className="eyebrow">Main agent</dt>
        <dd className="mt-1.5 flex items-center gap-2 text-sm text-bone">
          {main?.iconUrl ? (
            <Image src={main.iconUrl} alt="" width={24} height={24} unoptimized className="size-6 border border-line bg-ink" />
          ) : null}
          {profile.mainAgent ?? <span className="text-dim">—</span>}
        </dd>
      </div>
      <div>
        <dt className="eyebrow">Agent pool</dt>
        <dd className="mt-1.5">
          {pool.length === 0 ? <span className="text-sm text-dim">—</span> : (
            <ul className="flex flex-wrap gap-1">
              {pool.map(({ name, agent }) => (
                <li key={name} title={name}>
                  {agent?.iconUrl ? (
                    <Image src={agent.iconUrl} alt={name} width={24} height={24} unoptimized className="size-6 border border-line bg-ink" />
                  ) : (
                    <span className="border border-line px-1.5 py-0.5 font-display text-[0.625rem] uppercase tracking-widest text-muted">{name}</span>
                  )}
                </li>
              ))}
            </ul>
          )}
        </dd>
      </div>
      {profile.bio ? (
        <div className="sm:col-span-3">
          <dt className="eyebrow">Notes for captains</dt>
          <dd className="mt-1.5 whitespace-pre-line text-sm leading-relaxed text-muted">{profile.bio}</dd>
        </div>
      ) : null}
    </dl>
  );
}

/**
 * Who a captain has nominated, while the admin gets ready to open bidding: their full card
 * and notes, so the room can size them up before the clock starts.
 */
function NominatedCard({
  player,
  profile,
  teamName,
  agents,
  ranks,
}: {
  player: PlayerSummary;
  profile: ProfileView | undefined;
  teamName: string | null;
  agents: AgentView[];
  ranks: Map<string, RankView>;
}) {
  return (
    <div className="corner-cut bg-panel p-6 sm:p-8">
      <p className="eyebrow">{teamName ? `Nominated by ${teamName}` : "Nominated"}</p>
      <p className="mt-1 text-sm text-muted">Bidding opens once the admin starts the clock.</p>
      {profile?.nerfTier ? <div className="mt-3"><NerfLabel tier={profile.nerfTier} /></div> : null}
      <div className="mt-5">
        {profile ? (
          <PlayerCard username={profile.username} riotId={profile.riotId} playerCard={profile.playerCard}
            mainAgent={profile.mainAgent} currentRank={profile.currentRank} peakRank={profile.peakRank}
            agents={agents} ranks={[...new Set(ranks.values())]}
            primaryRole={profile.primaryRole} secondaryRole={profile.secondaryRole} agentPool={profile.agents} bio={profile.bio}
            bannerUrl={profile.bannerUrl} bannerColor={profile.accentColor} avatarUrl={profile.avatarUrl}
            layout="split" />
        ) : (
          // No full profile loaded (e.g. deleted mid-draft): the summary is all there is.
          <div className="flex items-center gap-4">
            <Avatar src={player.avatarUrl} name={player.username} size={64} />
            <div>
              <h2 className="text-4xl uppercase leading-none tracking-tight">{player.username}</h2>
              <div className="mt-3"><RankBadge name={player.currentRank} ranks={ranks} size={24} /></div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

/* --------------------------------------------------------------------- result */

/** How the last player went. Stays up until the next one. */
function ResultCard({
  result,
  myTeamId,
  ranks,
}: {
  result: LastResult;
  myTeamId?: number;
  ranks: Map<string, RankView>;
}) {
  const { lot } = result;
  const sold = lot.status === "SOLD";

  return (
    <div className="corner-cut bg-panel p-6 sm:p-8">
      <div className="flex flex-wrap items-start justify-between gap-6">
        <div className="flex items-start gap-4">
          <Avatar src={lot.player.avatarUrl} name={lot.player.username} size={56} />
          <div>
            <p className="eyebrow">Lot {lot.seq} · result</p>
            <h2 className="mt-1 text-4xl uppercase leading-none tracking-tight">
              <PlayerName profileId={lot.player.profileId}>{lot.player.username}</PlayerName>
            </h2>
            <div className="mt-2">
              <RankBadge name={lot.player.currentRank} ranks={ranks} size={22} />
            </div>
          </div>
        </div>

        <div className="text-right">
          <p className="eyebrow">{!sold ? "Unsold" : lot.winningBid === 0 ? "No bids, kept by" : "Sold for"}</p>
          {sold && lot.winningBid > 0 ? (
            <p className="tabular mt-1 font-display text-6xl font-semibold leading-none text-signal">{lot.winningBid}</p>
          ) : null}
          <p className="mt-1 flex items-center justify-end gap-2 font-display text-lg uppercase tracking-wide">
            {sold ? lot.winningTeamName : <span className="text-dim">Back in the queue</span>}
            {sold && lot.winningTeamId === myTeamId ? <Tag tone="accent">You</Tag> : null}
          </p>
        </div>
      </div>
    </div>
  );
}

/**
 * Counts down from the server's `endsAt`, not from a local duration, so every
 * client agrees and an anti-snipe extension shows up immediately.
 */
function Countdown({
  endsAt,
  serverTime,
  offsetRef,
  paused,
}: {
  endsAt: string | null;
  serverTime: string;
  offsetRef: RefObject<number>;
  paused: boolean;
}) {
  const target = endsAt ? new Date(endsAt).getTime() : 0;
  // Seeded from the snapshot's own clock so server and client first paint agree.
  const seeded = Math.max(0, (target - new Date(serverTime).getTime()) / 1000);

  // `longest` is the denominator for the progress rule. It tracks the largest window
  // seen for this lot, so an anti-snipe extension widens the bar instead of
  // overflowing it. The component is keyed by lot, so it resets on its own.
  const [clock, setClock] = useState(() => ({
    remaining: seeded,
    longest: Math.max(1, seeded),
  }));

  // The last five seconds tick, and the end sounds; nothing plays for a round that was
  // already that far gone when the page loaded, only as the seconds actually change.
  const soundOn = useSoundsEnabled();
  const lastSecond = useRef(Math.ceil(seeded));
  const secondNow = Math.ceil(clock.remaining);
  useEffect(() => {
    const previous = lastSecond.current;
    lastSecond.current = secondNow;
    if (!soundOn || paused || !endsAt || secondNow === previous) return;
    // The close itself (time up, or won) sounds when the result lands, not at zero here.
    if (secondNow >= 1 && secondNow <= 5) playTick();
  }, [secondNow, soundOn, paused, endsAt]);

  useEffect(() => {
    if (!target) return;
    const id = setInterval(() => {
      const remaining = Math.max(0, (target - (Date.now() + offsetRef.current)) / 1000);
      setClock((prev) => ({
        remaining,
        longest: Math.max(prev.longest, remaining),
      }));
    }, 200);
    return () => clearInterval(id);
  }, [target, offsetRef]);

  if (paused || !endsAt) {
    return (
      <div className="text-right">
        <p className="eyebrow">Time</p>
        <p className="tabular mt-1 font-display text-5xl font-semibold leading-none text-dim">
          {paused ? "Paused" : "—"}
        </p>
      </div>
    );
  }

  const { remaining, longest } = clock;
  const pct = Math.min(100, (remaining / longest) * 100);
  const urgent = remaining <= 5;

  return (
    <div className="min-w-40 text-right">
      <p className="eyebrow">Time</p>
      <p
        className={`tabular mt-1 font-display text-5xl font-semibold leading-none ${
          urgent ? "text-signal" : "text-bone"
        }`}
        aria-live="off"
      >
        {Math.ceil(remaining)}
        <span className="ml-1 text-base text-dim">s</span>
      </p>
      <div className="mt-3 h-0.5 w-full bg-raise">
        <div
          className={`h-full transition-[width] duration-200 ease-linear ${
            urgent ? "bg-accent" : "bg-muted"
          }`}
          style={{ width: `${pct}%` }}
        />
      </div>
    </div>
  );
}

/* -------------------------------------------------------------- bid controls */

/** Mirrors BidService.BID_RESET_SECONDS: how long each bid keeps the lot open. */
const BID_RESET_SECONDS = 10;

function BidControls({
  snapshot,
  me,
  myTeam,
  rejection,
  onBid,
  offsetRef,
  connection,
}: {
  snapshot: AuctionSnapshot;
  me: Me | null;
  myTeam: AuctionTeamView | undefined;
  rejection: string | null;
  onBid: (lotId: number, amount: number) => void;
  connection: ConnectionState;
  offsetRef: RefObject<number>;
}) {
  const lot = snapshot.currentLot;
  const deadline = lot?.endsAt ? new Date(lot.endsAt).getTime() : 0;

  const [amount, setAmount] = useState("");
  const [expired, setExpired] = useState(
    () => deadline > 0 && deadline <= new Date(snapshot.serverTime).getTime(),
  );

  // Locks the controls the moment the clock hits zero rather than waiting for the
  // server's close message to land. A bid pushes the deadline back, which re-arms this.
  useEffect(() => {
    if (!deadline) return;
    const id = setInterval(() => {
      setExpired(deadline - (Date.now() + offsetRef.current) <= 0);
    }, 200);
    return () => clearInterval(id);
  }, [deadline, offsetRef]);

  const floor = lot?.minBid ?? 1;
  const ceiling = myTeam?.maxBid ?? 0;
  const leading = lot !== null && myTeam !== undefined && lot.winningTeamId === myTeam.teamId;
  // After what it reads, or it runs into their temporal dead zone.
  const blocked = blockingReason();

  // One click to take the lead by 1, 5 or 10 over the current price, never past what the
  // team holds. Each button says the exact amount it bids.
  const raises = [floor, floor + 4, floor + 9]
    .filter((value) => value <= ceiling)
    .map((value) => ({ label: `Bid ${value}`, value }));

  function blockingReason(): string | null {
    if (snapshot.status === "COMPLETE") return "Draft finished.";
    if (!me) return "Sign in to bid.";
    if (connection !== "live") return "Reconnecting…";
    if (!myTeam) return "Only captains can bid.";
    if (snapshot.status === "PAUSED") return "Paused.";
    if (snapshot.status !== "LIVE") return "Draft hasn’t started.";
    if (!lot) return "Waiting for the next player.";
    if (expired) return "Time’s up.";
    if (myTeam.rosterCount >= myTeam.rosterSize) return "Your roster is full.";
    if (leading) return "You hold this player. Wait to be outbid.";
    if (ceiling < floor) return `Not enough credits to beat ${floor - 1}.`;
    return null;
  }

  function send(value: number) {
    if (!lot || blocked || !Number.isSafeInteger(value) || value < floor || value > ceiling) return;
    onBid(lot.lotId, value);
    setAmount("");
  }

  function submit(event: React.FormEvent) {
    event.preventDefault();
    if (amount !== "") send(Number(amount));
  }

  return (
    <div className="bg-panel p-5 sm:px-8">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex flex-wrap items-center gap-2">
          {raises.map((option, index) => (
            <button
              key={option.label}
              type="button"
              onClick={() => send(option.value)}
              disabled={blocked !== null}
              {...NO_FORM_RESTORE}
              className={index === 0
                ? "corner-cut-sm tabular bg-accent px-5 py-2.5 font-display text-base font-semibold uppercase tracking-wider text-white transition-colors hover:bg-accent-deep disabled:cursor-not-allowed disabled:bg-raise disabled:text-dim"
                : "tabular border border-line px-3 py-2.5 font-display text-xs font-semibold uppercase tracking-wider text-muted transition-colors hover:border-dim hover:text-bone disabled:opacity-30"}
            >
              {option.label}
            </button>
          ))}
          <form onSubmit={submit} className="flex items-center gap-2">
            <label className="sr-only" htmlFor="live-bid">
              Custom bid
            </label>
            <input
              id="live-bid"
              value={amount}
              onChange={(e) => setAmount(e.target.value.replace(/[^\d]/g, ""))}
              inputMode="numeric"
              placeholder={`${floor}+`}
              disabled={blocked !== null}
              {...NO_FORM_RESTORE}
              className="tabular w-24 border border-line bg-ink px-3 py-2.5 text-base text-bone placeholder:text-dim focus:border-accent focus:outline-none disabled:opacity-40"
            />
            <button
              type="submit"
              disabled={blocked !== null || amount === "" || Number(amount) < floor || Number(amount) > ceiling}
              {...NO_FORM_RESTORE}
              className="border border-line px-3 py-2.5 font-display text-xs font-semibold uppercase tracking-wider text-muted transition-colors hover:border-dim hover:text-bone disabled:opacity-30"
            >
              Bid
            </button>
          </form>
        </div>

        {myTeam ? (
          <dl className="tabular flex gap-6 text-sm">
            <div className="text-right">
              <dt className="eyebrow">Credits</dt>
              <dd className="font-display text-xl font-semibold">
                {myTeam.remainingCredits}
              </dd>
            </div>
          </dl>
        ) : null}
      </div>

      <p role="status" className="mt-3 min-h-5 text-sm">
        {rejection ? (
          <span className="text-signal">{rejection}</span>
        ) : blocked ? (
          <span className="text-dim">{blocked}</span>
        ) : (
          <span className="text-dim">
            Bid {floor}–{ceiling} to take the lead. Each bid gives the room {BID_RESET_SECONDS} more seconds to answer.
          </span>
        )}
      </p>
    </div>
  );
}

/* ---------------------------------------------------------------- final rosters */

/**
 * The finished draft: every team side by side, one player per row, captain first. Takes
 * over the whole room so nobody wonders whether it's still going.
 */
function FinalRosters({
  teams,
  profiles,
}: {
  teams: AuctionTeamView[];
  profiles: Map<number, ProfileView>;
}) {
  const rosters = teams.map((team) =>
    [...team.roster].sort(
      (a, b) => Number(b.username === team.captainUsername) - Number(a.username === team.captainUsername),
    ),
  );
  const rows = Math.max(0, ...rosters.map((r) => r.length));

  return (
    <section aria-labelledby="final-rosters" className="bg-panel">
      <div className="flex flex-wrap items-baseline justify-between gap-3 border-b border-line-soft px-5 py-5 sm:px-8">
        <h2 id="final-rosters" className="font-display text-3xl uppercase tracking-wide">Draft complete</h2>
        <p className="text-sm text-muted">Final rosters</p>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full min-w-max border-collapse text-left">
          <thead>
            <tr>
              {teams.map((team) => (
                <th key={team.teamId} scope="col"
                  className="min-w-48 border-b border-l border-line-soft px-5 py-3 align-bottom font-display text-base font-semibold uppercase tracking-wide first:border-l-0 sm:px-8">
                  {team.name}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {Array.from({ length: rows }, (_, row) => (
              <tr key={row}>
                {teams.map((team, col) => {
                  const entry = rosters[col][row];
                  return (
                    <td key={team.teamId} className="border-b border-l border-line-soft px-5 py-2.5 first:border-l-0 sm:px-8">
                      {entry ? (
                        <span className="flex items-center gap-3">
                          <Avatar src={profiles.get(entry.profileId)?.avatarUrl ?? null} name={entry.username} size={28} />
                          <span className="min-w-0 truncate text-sm text-bone">
                            <PlayerName profileId={entry.profileId}>{entry.username}</PlayerName>
                          </span>
                          {entry.username === team.captainUsername ? (
                            <span className="shrink-0 text-xs uppercase tracking-wider text-dim">Captain</span>
                          ) : null}
                        </span>
                      ) : null}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

/* ------------------------------------------------------------------ the rail */

/**
 * The team size the draft will set when it starts: everyone seated (the captains) plus
 * everyone queued, divided across the teams and rounded up, as the server does it. Until
 * then the stored size is only a default.
 */
function projectedTeamSize(snapshot: AuctionSnapshot): number | null {
  const teams = snapshot.teams.length;
  if (teams === 0) return null;
  const seated = snapshot.teams.reduce((n, team) => n + team.rosterCount, 0);
  const most = Math.max(...snapshot.teams.map((team) => team.rosterCount));
  return Math.max(most, Math.ceil((seated + snapshot.pendingLots) / teams));
}

function TeamsRail({
  teams,
  nominatingTeamId,
  myTeamId,
  leadingTeamId,
  creditBudget,
  teamSize,
}: {
  teams: AuctionTeamView[];
  /** The team whose captain is nominating right now, if anyone. */
  nominatingTeamId: number | null;
  myTeamId?: number;
  /** Who holds the player up for bidding right now. */
  leadingTeamId: number | null;
  creditBudget: number;
  /** Before the draft starts, the size it will set; otherwise each team's own. */
  teamSize: number | null;
}) {
  return (
    <div className="bg-panel">
      <div className="border-b border-line-soft px-5 py-3">
        <p className="eyebrow">Teams</p>
      </div>
      <ul>
        {/* Already in nominating order, so the team that goes first is on top. */}
        {teams.map((team, index) => {
          const mine = team.teamId === myTeamId;
          const leading = team.teamId === leadingTeamId;
          const spent = creditBudget - team.remainingCredits;
          return (
            <li
              key={team.teamId}
              className={`border-b border-line-soft px-5 py-4 ${
                mine ? "bg-raise" : ""
              }`}
            >
              <div className="flex items-baseline justify-between gap-3">
                <p className="flex items-center gap-2 truncate font-display text-base uppercase tracking-wide">
                  {leading ? (
                    <span
                      aria-label="Leading"
                      title="Leading"
                      className="block size-1.5 bg-accent"
                    />
                  ) : null}
                  <span aria-label={`Nominates ${index + 1}`} className="tabular shrink-0 text-dim">{index + 1}</span>
                  <span className="truncate">{team.name}</span>
                  {mine ? <Tag>You</Tag> : null}
                  {team.teamId === nominatingTeamId ? (
                    <span className="turn-tag"><span className="turn-dot" aria-hidden />Nominating</span>
                  ) : null}
                </p>
                <p className="tabular shrink-0 font-display text-lg font-semibold">
                  {team.remainingCredits}
                </p>
              </div>

              <div className="mt-2 h-0.5 w-full bg-line">
                <div
                  className="h-full bg-accent-deep"
                  style={{ width: `${(spent / creditBudget) * 100}%` }}
                />
              </div>

              <div className="tabular mt-2 flex justify-between text-xs text-dim">
                <span>
                  {team.rosterCount}/{teamSize ?? team.rosterSize} players
                </span>
              </div>

              {team.roster.length > 0 ? (
                <ul className="mt-2 space-y-1 text-sm text-muted">
                  {team.roster.map((entry) => (
                    <li key={entry.profileId} className="flex items-baseline justify-between gap-3">
                      <span className="min-w-0 truncate">
                        <PlayerName profileId={entry.profileId}>{entry.username}</PlayerName>
                      </span>
                      {entry.username === team.captainUsername ? (
                        <span className="shrink-0 text-xs uppercase tracking-wider text-dim">Captain</span>
                      ) : null}
                    </li>
                  ))}
                </ul>
              ) : null}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
