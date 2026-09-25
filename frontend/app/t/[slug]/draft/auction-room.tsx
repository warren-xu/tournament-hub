"use client";

import { useEffect, useState, type RefObject } from "react";
import { AdminRail } from "./admin-rail";
import { Avatar, RankBadge, rankIndex, Tag } from "@/components/ui";
import {
  useAuction,
  type Assignment,
  type ConnectionState,
  type Reveal,
} from "@/lib/use-auction";
import { timeOfDay } from "@/lib/format";
import type {
  AuctionSnapshot,
  AuctionTeamView,
  Me,
  RankView,
} from "@/lib/types";

export function AuctionRoom({
  initial,
  me,
  ranks,
  rosterSize,
  creditBudget,
}: {
  initial: AuctionSnapshot;
  me: Me | null;
  ranks: RankView[];
  rosterSize: number;
  creditBudget: number;
}) {
  const {
    snapshot,
    connection,
    feed,
    rejection,
    reveal,
    assignments,
    consumeAssignment,
    yourBid,
    submitBid,
    applySnapshot,
    clockOffset,
  } = useAuction(initial.auctionId, initial);

  const rankLookup = rankIndex(ranks);
  const lot = snapshot.currentLot;
  const myTeam = me
    ? snapshot.teams.find((t) => t.captainUserId === me.userId)
    : undefined;

  return (
    <div className="auction-room space-y-5">
      <StatusBar
        connection={connection}
        snapshot={snapshot}
        pending={snapshot.pendingLots}
      />

      <div className="grid items-start gap-5 lg:grid-cols-[1.7fr_1fr]">
        <div className="space-y-5">
          {assignments.length > 0 ? (
            <RandomFillRoulette
              key={assignments[0].lotId}
              assignment={assignments[0]}
              teams={snapshot.teams}
              onDone={consumeAssignment}
            />
          ) : null}
          <LotCard
            snapshot={snapshot}
            reveal={reveal}
            offsetRef={clockOffset}
            myTeamId={myTeam?.teamId}
            ranks={rankLookup}
          />
          {myTeam ? <BidControls
            key={lot?.lotId ?? "idle"}
            snapshot={snapshot}
            me={me}
            myTeam={myTeam}
            yourBid={yourBid}
            rejection={rejection?.message ?? null}
            onBid={submitBid}
            offsetRef={clockOffset}
            connection={connection}
          /> : <p className="border-l-2 border-line bg-panel px-5 py-4 text-sm text-muted">Spectator view · Follow each pick and bid reveal live. No sign-in needed.</p>}
          <details className="border border-line-soft bg-panel">
            <summary className="px-5 py-3 font-display text-base uppercase tracking-wide">Draft activity</summary>
            <Feed entries={feed} />
          </details>
        </div>

        <div className="space-y-5">
          <TeamsRail
            teams={snapshot.teams}
            myTeamId={myTeam?.teamId}
            lockedInTeamIds={lot?.lockedInTeamIds ?? []}
            creditBudget={creditBudget}
            rosterSize={rosterSize}
          />
          {me?.role === "ADMIN" ? (
            <AdminRail snapshot={snapshot} onSnapshot={applySnapshot} />
          ) : null}
        </div>
      </div>
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
  connection,
  snapshot,
  pending,
}: {
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

      <div className="ml-auto flex items-center gap-2">
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
  reveal,
  offsetRef,
  myTeamId,
  ranks,
}: {
  snapshot: AuctionSnapshot;
  reveal: Reveal | null;
  offsetRef: RefObject<number>;
  myTeamId?: number;
  ranks: Map<string, RankView>;
}) {
  const lot = snapshot.currentLot;

  if (!lot) {
    // The last reveal stays up between players: it is the only time the amounts exist.
    if (reveal) {
      return <RevealCard reveal={reveal} myTeamId={myTeamId} ranks={ranks} />;
    }
    return (
      <div className="flex min-h-72 flex-col items-center justify-center bg-panel px-6 py-16 text-center">
        <p className="font-display text-2xl uppercase tracking-wide text-dim">
          {snapshot.pendingLots > 0 ? "Waiting for next player" : "No players left"}
        </p>
      </div>
    );
  }

  const lockedIn = lot.lockedInTeamIds.length;
  const waitingOn = Math.max(0, lot.captainsExpected - lockedIn);
  const iAmIn = myTeamId !== undefined && lot.lockedInTeamIds.includes(myTeamId);

  return (
    <div className="corner-cut bg-panel p-6 sm:p-8">
      <div className="flex flex-wrap items-start justify-between gap-6">
        <div className="flex items-start gap-4">
          <Avatar src={lot.player.avatarUrl} name={lot.player.username} size={64} />
          <div>
            <p className="eyebrow">Lot {lot.seq}</p>
            <h2 className="mt-1 text-4xl uppercase leading-none tracking-tight sm:text-5xl">
              {lot.player.username}
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

      <div className="mt-8 flex flex-wrap items-end justify-between gap-6 border-t border-line-soft pt-6">
        <div>
          <p className="eyebrow">Bids in</p>
          <p className="tabular mt-1 font-display text-6xl font-semibold leading-none">
            {lockedIn}
            <span className="text-dim">/{lot.captainsExpected}</span>
          </p>
          <ul aria-hidden className="mt-3 flex gap-1.5">
            {Array.from({ length: lot.captainsExpected }, (_, i) => (
              <li
                key={i}
                className={`h-1 w-7 ${i < lockedIn ? "bg-accent" : "bg-raise"}`}
              />
            ))}
          </ul>
        </div>
        <p className="flex items-center gap-2 text-sm text-muted">
          {iAmIn ? <Tag tone="accent">Your bid is in</Tag> : null}
          {waitingOn === 0 ? "All bids in" : `Waiting on ${waitingOn}`}
        </p>
      </div>
    </div>
  );
}

/* --------------------------------------------------------------------- reveal */

/** Every captain's sealed bid, opened at once. Stays up until the next player. */
function RevealCard({
  reveal,
  myTeamId,
  ranks,
}: {
  reveal: Reveal;
  myTeamId?: number;
  ranks: Map<string, RankView>;
}) {
  const { lot, bids } = reveal;
  const sold = lot.status === "SOLD";
  const top = bids.length > 0 ? bids[0].amount : 0;
  const tied = bids.filter((bid) => bid.amount === top).length > 1;

  return (
    <div className="corner-cut bg-panel p-6 sm:p-8">
      <div className="flex flex-wrap items-start justify-between gap-6">
        <div className="flex items-start gap-4">
          <Avatar src={lot.player.avatarUrl} name={lot.player.username} size={56} />
          <div>
            <p className="eyebrow">Lot {lot.seq} · result</p>
            <h2 className="mt-1 text-4xl uppercase leading-none tracking-tight">
              {lot.player.username}
            </h2>
            <div className="mt-2">
              <RankBadge name={lot.player.currentRank} ranks={ranks} size={22} />
            </div>
          </div>
        </div>

        <div className="text-right">
          <p className="eyebrow">{sold ? "Sold for" : "Unsold"}</p>
          <p
            className={`tabular mt-1 font-display text-6xl font-semibold leading-none ${
              sold ? "text-signal" : "text-dim"
            }`}
          >
            {sold ? lot.winningBid : "—"}
          </p>
          <p className="mt-1 font-display text-lg uppercase tracking-wide">
            {sold ? lot.winningTeamName : null}
          </p>
          {tied ? (
            <p className="mt-1 text-xs uppercase tracking-widest text-signal">
              Tie, picked at random
            </p>
          ) : null}
        </div>
      </div>

      {bids.length > 0 ? (
        <ol className="mt-8 space-y-px border-t border-line-soft pt-6">
          {bids.map((bid, index) => {
            const won = bid.teamId === lot.winningTeamId;
            return (
              <li
                key={bid.bidId}
                className={`bid-reveal flex items-baseline justify-between gap-4 px-3 py-2 ${
                  won ? "bg-raise" : ""
                }`}
                style={{ animationDelay: `${index * 90}ms` }}
              >
                <span className="flex items-center gap-2 truncate font-display text-base uppercase tracking-wide">
                  {won ? <span aria-hidden className="block size-1.5 bg-accent" /> : null}
                  <span className="truncate">{bid.teamName ?? "A team"}</span>
                  {bid.teamId === myTeamId ? <Tag>You</Tag> : null}
                </span>
                <span
                  className={`tabular shrink-0 font-display text-2xl font-semibold ${
                    won ? "text-signal" : "text-muted"
                  }`}
                >
                  {bid.amount}
                </span>
              </li>
            );
          })}
        </ol>
      ) : (
        <p className="mt-8 border-t border-line-soft pt-6 text-sm text-dim">
          No bids. Back in the queue.
        </p>
      )}
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

function BidControls({
  snapshot,
  me,
  myTeam,
  yourBid,
  rejection,
  onBid,
  offsetRef,
  connection,
}: {
  snapshot: AuctionSnapshot;
  me: Me | null;
  myTeam: AuctionTeamView | undefined;
  yourBid: number | null;
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
  // server's close message to land.
  useEffect(() => {
    if (!deadline) return;
    const id = setInterval(() => {
      setExpired(deadline - (Date.now() + offsetRef.current) <= 0);
    }, 200);
    return () => clearInterval(id);
  }, [deadline, offsetRef]);

  const floor = lot?.minBid ?? 0;
  const ceiling = myTeam?.maxBid ?? 0;
  // After the two it reads, or it runs into their temporal dead zone.
  const blocked = blockingReason();

  // No ladder to climb, so the shortcuts are shares of what is left rather than
  // increments over someone else's bid.
  const shortcuts = [
    { label: "Min", value: floor },
    { label: "Half", value: Math.floor(ceiling / 2) },
    { label: "Max", value: ceiling },
  ].filter(
    (option, i, all) =>
      option.value >= floor
      && option.value <= ceiling
      && all.findIndex((other) => other.value === option.value) === i,
  );

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
    if (myTeam.maxBid < floor)
      return `Not enough credits to bid (min ${floor}). Your open spots will be filled at random.`;
    return null;
  }

  function submit(event: React.FormEvent) {
    event.preventDefault();
    const parsed = Number(amount);
    if (!lot || blocked || !Number.isSafeInteger(parsed) || amount === "" || parsed < floor || parsed > ceiling) return;
    onBid(lot.lotId, Math.trunc(parsed));
    setAmount("");
  }

  return (
    <div className="bg-panel p-5 sm:px-8">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <form onSubmit={submit} className="flex flex-wrap items-center gap-2">
          <label className="sr-only" htmlFor="sealed-bid">
            Your bid
          </label>
          <input
            id="sealed-bid"
            value={amount}
            onChange={(e) => setAmount(e.target.value.replace(/[^\d]/g, ""))}
            inputMode="numeric"
            placeholder={floor ? String(floor) : "—"}
            disabled={blocked !== null}
            className="tabular w-28 border border-line bg-ink px-3 py-2.5 text-base text-bone placeholder:text-dim focus:border-accent focus:outline-none disabled:opacity-40"
          />
          <button
            type="submit"
            disabled={blocked !== null || amount === "" || !Number.isSafeInteger(Number(amount)) || Number(amount) < floor || Number(amount) > ceiling}
            className="corner-cut-sm bg-accent px-5 py-2.5 font-display text-base font-semibold uppercase tracking-wider text-white transition-colors hover:bg-accent-deep disabled:cursor-not-allowed disabled:bg-raise disabled:text-dim"
          >
            {yourBid === null ? "Bid" : "Update bid"}
          </button>

          {shortcuts.map((option) => (
            <button
              key={option.label}
              type="button"
              onClick={() => setAmount(String(option.value))}
              disabled={blocked !== null}
              className="tabular border border-line px-3 py-2.5 font-display text-xs font-semibold uppercase tracking-wider text-muted transition-colors hover:border-dim hover:text-bone disabled:opacity-30"
            >
              {option.label} {option.value}
            </button>
          ))}
        </form>

        {myTeam ? (
          <dl className="tabular flex gap-6 text-sm">
            <div className="text-right">
              <dt className="eyebrow">Credits</dt>
              <dd className="font-display text-xl font-semibold">
                {myTeam.remainingCredits}
              </dd>
            </div>
            <div className="text-right">
              <dt className="eyebrow">Your bid</dt>
              <dd
                className={`font-display text-xl font-semibold ${
                  yourBid === null ? "text-dim" : "text-signal"
                }`}
              >
                {yourBid ?? "—"}
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
        ) : yourBid !== null ? (
          <span className="text-dim">
            Hidden until the reveal. You can change it until the round closes.
          </span>
        ) : (
          <span className="text-dim">
            Bid {floor}–{ceiling}. Bids are hidden; highest wins, ties are random.
          </span>
        )}
      </p>
    </div>
  );
}

/* -------------------------------------------------------------------- roulette */

/**
 * The end-of-auction draw. Nobody has credits left, so the remaining players are dealt
 * out - and a result that just appears reads like a bug, so it spins through the
 * captains before landing on the one it already belongs to.
 */
const ROULETTE_STEPS = 26;

function RandomFillRoulette({
  assignment,
  teams,
  onDone,
}: {
  assignment: Assignment;
  teams: AuctionTeamView[];
  onDone: (lotId: number) => void;
}) {
  // Keyed by lot at the call site, so this state belongs to exactly one draw. A reader
  // who asked for less motion gets the result without the spin.
  const [step, setStep] = useState(() =>
    teams.length < 2
    || (typeof window !== "undefined"
        && window.matchMedia("(prefers-reduced-motion: reduce)").matches)
      ? ROULETTE_STEPS
      : 0,
  );

  const landed = step >= ROULETTE_STEPS;
  const winnerIndex = Math.max(
    0,
    teams.findIndex((team) => team.teamId === assignment.teamId),
  );

  useEffect(() => {
    const lotId = assignment.lotId;
    if (landed) {
      const hold = setTimeout(() => onDone(lotId), 1300);
      return () => clearTimeout(hold);
    }
    // Each step is slower than the last, the way a real wheel gives up its momentum.
    const timer = setTimeout(() => setStep((current) => current + 1), 45 + step * 5);
    return () => clearTimeout(timer);
  }, [assignment.lotId, landed, step, onDone]);

  const showing = landed
    ? teams[winnerIndex]
    : teams[step % Math.max(1, teams.length)];

  return (
    <div className="corner-cut bg-panel p-6 text-center sm:p-8">
      <p className="eyebrow">Random assignment</p>

      <div className="mt-5 flex items-center justify-center gap-4">
        <Avatar src={assignment.avatarUrl} name={assignment.username} size={48} />
        <span className="font-display text-3xl uppercase tracking-tight">
          {assignment.username}
        </span>
      </div>

      <p aria-hidden className="mt-4 font-display text-sm uppercase tracking-widest text-dim">
        to
      </p>

      <p
        className={`roulette-slot mt-2 font-display text-5xl font-semibold uppercase leading-none tracking-tight ${
          landed ? "roulette-landed text-signal" : "text-muted"
        }`}
        aria-live="polite"
      >
        {landed ? (assignment.teamName ?? showing?.name) : showing?.name}
      </p>

      <p className="mt-4 min-h-5 text-sm text-dim">
        {landed ? "Free. No team had credits left." : null}
      </p>
    </div>
  );
}

/* ------------------------------------------------------------------ the rail */

function TeamsRail({
  teams,
  myTeamId,
  lockedInTeamIds,
  creditBudget,
  rosterSize,
}: {
  teams: AuctionTeamView[];
  myTeamId?: number;
  lockedInTeamIds: number[];
  creditBudget: number;
  rosterSize: number;
}) {
  return (
    <div className="bg-panel">
      <div className="border-b border-line-soft px-5 py-3">
        <p className="eyebrow">Teams</p>
      </div>
      <ul>
        {teams.map((team) => {
          const mine = team.teamId === myTeamId;
          const lockedIn = lockedInTeamIds.includes(team.teamId);
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
                  {lockedIn ? (
                    <span
                      aria-label="Bid in"
                      title="Bid in"
                      className="block size-1.5 bg-accent"
                    />
                  ) : null}
                  <span className="truncate">{team.name}</span>
                  {mine ? <Tag>You</Tag> : null}
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
                  {team.rosterCount}/{rosterSize} players
                </span>
                <span>max bid {team.maxBid}</span>
              </div>

              {team.roster.length > 0 ? (
                <ul className="mt-2 flex flex-wrap gap-x-3 gap-y-1 text-xs text-muted">
                  {team.roster.map((entry) => (
                    <li key={entry.profileId} className="tabular">
                      {entry.username}
                      <span className="text-dim"> {entry.pricePaid}</span>
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

function Feed({ entries }: { entries: ReturnType<typeof useAuction>["feed"] }) {
  return (
    <div className="bg-panel">
      <div className="border-b border-line-soft px-5 py-3 sm:px-8">
        <p className="eyebrow">Log</p>
      </div>
      {entries.length === 0 ? (
        <p className="px-5 py-6 text-sm text-dim sm:px-8">
          No activity yet.
        </p>
      ) : (
        <ul className="max-h-72 overflow-y-auto">
          {entries.map((entry) => (
            <li
              key={entry.id}
              className="flex items-baseline justify-between gap-4 border-b border-line-soft px-5 py-2.5 text-sm sm:px-8"
            >
              <span
                className={entry.kind === "event" ? "text-muted" : "text-bone"}
              >
                {entry.text}
                {entry.amount !== undefined ? (
                  <span className="tabular ml-2 font-display text-base font-semibold text-signal">
                    {entry.amount}
                  </span>
                ) : null}
              </span>
              <span className="tabular shrink-0 font-mono text-xs text-dim">
                {timeOfDay(entry.at)}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
