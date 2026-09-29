"use client";

import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { PlayerCard } from "@/components/player-card";
import { LocalTime } from "@/components/local-time";
import { buttonClass, LinkButton, Tag } from "@/components/ui";
import type { CalendarEvent } from "@/lib/calendar";
import { api, ApiCallError } from "@/lib/client-api";
import type { AgentView, ProfileView, RankView, TournamentView } from "@/lib/types";
import { CalendarLinks } from "./calendar-links";

/**
 * Joining the draft pool. The dialog shows the card captains will see, so a player
 * checks it before committing; signing up puts them straight into the auction queue.
 */
export function SignUp({
  tournament,
  signedIn,
  profile,
  signedUp,
  locked,
  event,
  agents,
  ranks,
}: {
  tournament: TournamentView;
  signedIn: boolean;
  profile: ProfileView | null;
  signedUp: boolean;
  /** The draft has started: nobody can join or leave any more. */
  locked: boolean;
  event: CalendarEvent | null;
  agents: AgentView[];
  ranks: RankView[];
}) {
  const router = useRouter();
  const dialog = useRef<HTMLDialogElement>(null);
  const [busy, setBusy] = useState(false);
  const [open, setOpen] = useState(false);
  const [joined, setJoined] = useState(false);
  const [confirmLeave, setConfirmLeave] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!signedIn) {
    return <LinkButton href="/signin" tone="primary">Sign in to sign up</LinkButton>;
  }

  async function call(path: string, method: "POST" | "DELETE", fallback: string) {
    setBusy(true);
    setError(null);
    try {
      await api(path, { method });
      return true;
    } catch (err) {
      setError(err instanceof ApiCallError ? err.message : fallback);
      return false;
    } finally {
      setBusy(false);
    }
  }

  // Straight after joining, the badge shows before the refreshed page confirms it.
  if ((signedUp || joined) && !open) {
    return (
      <div className="flex flex-col items-start gap-2">
        <div className="flex flex-wrap items-center gap-3">
          <Tag tone="accent">You&rsquo;re in the queue</Tag>
          {locked ? null : confirmLeave ? (
            <span className="flex items-center gap-2 text-sm text-muted">
              Leave the queue?
              <button type="button" disabled={busy} className={buttonClass("default")}
                onClick={async () => {
                  if (await call(`/api/tournaments/${tournament.id}/registrations/me`, "DELETE", "Couldn't take you out of the queue.")) {
                    setConfirmLeave(false);
                    setJoined(false);
                    router.refresh();
                  }
                }}>
                {busy ? "Leaving…" : "Yes, leave"}
              </button>
              <button type="button" disabled={busy} className={buttonClass("ghost")} onClick={() => setConfirmLeave(false)}>
                Keep my spot
              </button>
            </span>
          ) : (
            <button type="button" className={buttonClass("ghost")} onClick={() => setConfirmLeave(true)}>
              Leave the queue
            </button>
          )}
        </div>
        {error ? <p role="alert" className="max-w-72 text-sm text-signal">{error}</p> : null}
      </div>
    );
  }

  // What stops this player signing up, in their words. Mirrors the backend's check.
  const missing = !profile
    ? ["You don’t have a player profile yet."]
    : [!profile.currentRank && "Current rank"].filter((m): m is string => Boolean(m));
  const ready = missing.length === 0;

  function close() {
    dialog.current?.close();
  }

  return (
    <>
      <button type="button" className={buttonClass("primary")} aria-haspopup="dialog"
        onClick={() => { setError(null); setOpen(true); dialog.current?.showModal(); }}>
        Sign up
      </button>

      <dialog ref={dialog} className="pool-dialog" aria-labelledby="sign-up-heading"
        onClose={() => { setOpen(false); if (joined) router.refresh(); }}
        onClick={(e) => { if (e.target === e.currentTarget) close(); }}>
        <div className="pool-dialog-content border border-line bg-panel">
          <button type="button" className="pool-dialog-close" onClick={close} aria-label="Close sign-up">✕</button>

          <header className="border-b border-line-soft px-6 pb-5 pt-6 pr-16">
            <p className="eyebrow">{joined ? "Signed up" : "Sign up"}</p>
            <h2 id="sign-up-heading" className="mt-2 font-display text-3xl uppercase leading-none tracking-wide">
              {tournament.name}
            </h2>
            <p className="mt-3 text-sm text-muted">
              {tournament.startsAt ? <LocalTime iso={tournament.startsAt} className="text-bone" /> : "Date to be announced"}
            </p>
          </header>

          {joined ? (
            <div className="space-y-5 px-6 py-6">
              <p className="text-base text-bone">You&rsquo;re in the draft queue.</p>
              <p className="text-sm leading-relaxed text-muted">
                Captains will bid for you in the auction. You can leave from the tournament page any time before the draft starts.
              </p>
              {event ? <CalendarLinks event={event} slug={tournament.slug} /> : null}
              <div className="flex justify-end border-t border-line-soft pt-5">
                <button type="button" autoFocus className={buttonClass("primary")} onClick={close}>Done</button>
              </div>
            </div>
          ) : (
            <div className="space-y-5 px-6 py-6">
              <ul className="space-y-2 text-sm leading-relaxed text-muted">
                <li>You go straight into the auction queue. No approval needed.</li>
                <li>Captains place sealed bids for you. Team size is set when the draft starts, from how many people sign up.</li>
                <li>Changed your mind? Leave any time before the draft starts.</li>
              </ul>

              {profile && ready ? (
                <div>
                  <div className="mb-3 flex items-baseline justify-between gap-3">
                    <p className="eyebrow">The card captains will see</p>
                    <a href="/profile" className="text-xs text-muted underline underline-offset-4 hover:text-bone">Edit profile</a>
                  </div>
                  <PlayerCard username={profile.username} riotId={profile.riotId} playerCard={profile.playerCard}
                    mainAgent={profile.mainAgent} currentRank={profile.currentRank} peakRank={profile.peakRank} agents={agents} ranks={ranks}
                    primaryRole={profile.primaryRole} secondaryRole={profile.secondaryRole} agentPool={profile.agents} bio={profile.bio}
                    bannerUrl={profile.bannerUrl} bannerColor={profile.accentColor} avatarUrl={profile.avatarUrl}
                    layout="split" />
                </div>
              ) : (
                <div className="text-sm">
                  <p className="text-bone">Your profile is missing:</p>
                  <ul className="mt-2 list-disc space-y-1 pl-5 text-signal">
                    {missing.map((item) => <li key={item}>{item}</li>)}
                  </ul>
                </div>
              )}

              {error ? <p role="alert" className="text-sm text-signal">{error}</p> : null}

              <div className="flex flex-wrap justify-end gap-3 border-t border-line-soft pt-5">
                <button type="button" className={buttonClass("ghost")} onClick={close}>Cancel</button>
                {ready ? (
                  <button type="button" disabled={busy} className={buttonClass("primary")}
                    onClick={async () => {
                      if (await call(`/api/tournaments/${tournament.id}/registrations`, "POST", "Couldn't sign you up.")) setJoined(true);
                    }}>
                    {busy ? "Signing up…" : "Sign me up"}
                  </button>
                ) : (
                  <LinkButton href="/profile" tone="primary">{profile ? "Add it to your profile" : "Create your profile"}</LinkButton>
                )}
              </div>
            </div>
          )}
        </div>
      </dialog>
    </>
  );
}
