"use client";

import { useEffect, useRef, type ReactNode } from "react";
import { PlayerCard } from "@/components/player-card";
import type { AgentView, ProfileView, RankView } from "@/lib/types";

/**
 * A player's full card in a modal: the pool and the draft room open the same one.
 * Open it by passing a profile; every way of closing (✕, Escape, the backdrop) ends in
 * `onClose`, where the owner clears its selection.
 */
export function PlayerDialog({
  profile,
  agents,
  ranks,
  onClose,
  footer,
}: {
  profile: ProfileView | null;
  agents: AgentView[];
  ranks: RankView[];
  onClose: () => void;
  /** Extra controls under the card, such as an admin's delete. */
  footer?: ReactNode;
}) {
  const dialog = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const element = dialog.current;
    if (!element) return;
    if (profile && !element.open) element.showModal();
    if (!profile && element.open) element.close();
  }, [profile]);

  return (
    <dialog ref={dialog} className="pool-dialog" aria-label={profile ? `${profile.username}'s player card` : "Player card"}
      onClose={onClose}
      onClick={(event) => { if (event.target === event.currentTarget) dialog.current?.close(); }}>
      {profile ? <div className="pool-dialog-content">
        <button type="button" autoFocus className="pool-dialog-close" onClick={() => dialog.current?.close()} aria-label="Close player card">✕</button>
        <PlayerCard username={profile.username} riotId={profile.riotId} playerCard={profile.playerCard}
          mainAgent={profile.mainAgent} currentRank={profile.currentRank} peakRank={profile.peakRank} agents={agents} ranks={ranks}
          primaryRole={profile.primaryRole} secondaryRole={profile.secondaryRole} agentPool={profile.agents} bio={profile.bio}
          bannerUrl={profile.bannerUrl} bannerColor={profile.accentColor} avatarUrl={profile.avatarUrl}
          nerfTier={profile.nerfTier}
          layout="split" />
        {footer ? <div className="border-t border-line bg-ink p-3">{footer}</div> : null}
      </div> : null}
    </dialog>
  );
}
