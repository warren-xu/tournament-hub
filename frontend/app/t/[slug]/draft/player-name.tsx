"use client";

import { createContext, useContext, type ReactNode } from "react";

/** Lets any player name in the draft room open that player's full card. */
export const ProfileOpener = createContext<{
  open: (profileId: number) => void;
  has: (profileId: number) => boolean;
} | null>(null);

/** A player's name as a button that opens their card; plain text if we have no profile for them. */
export function PlayerName({ profileId, children }: { profileId: number; children: ReactNode }) {
  const opener = useContext(ProfileOpener);
  if (!opener?.has(profileId)) return <>{children}</>;
  return (
    <button type="button" className="player-link" aria-haspopup="dialog" onClick={() => opener.open(profileId)}>
      {children}
    </button>
  );
}
