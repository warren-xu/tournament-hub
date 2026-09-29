import { EmptyState, LinkButton, OfflineNotice, SectionHead } from "@/components/ui";
import { PlayerPool } from "@/components/player-pool";
import {
  backendReachable,
  getAgents,
  getMe,
  getMyProfile,
  getProfiles,
  getRanks,
} from "@/lib/server-api";
import type { ProfileView } from "@/lib/types";

/** Signing in creates an empty profile, so "has a profile" means "has filled one in". */
function hasFilledIn(profile: ProfileView | null) {
  return Boolean(profile && (profile.riotId || profile.currentRank || profile.mainAgent));
}

export default async function HomePage() {
  const [online, profiles, ranks, agents, me] = await Promise.all([
    backendReachable(),
    getProfiles(),
    getRanks(true),
    getAgents(true),
    getMe(),
  ]);
  const mine = me ? await getMyProfile() : null;

  // The signed-in player always leads, so they're in the first block even when the arena is collapsed.
  const list = [...(profiles ?? [])].sort((a, b) => Number(b.id === mine?.id) - Number(a.id === mine?.id));
  const isAdmin = me?.role === "ADMIN";
  // Signed-out visitors land on /profile, which asks them to sign in first.
  const invite = online && !hasFilledIn(mine)
    ? <LinkButton href="/profile" tone="primary">Create your profile</LinkButton>
    : null;

  return (
    <div className="mx-auto max-w-[1400px] px-4 py-8 sm:px-6 sm:py-12">
      {!online ? (
        <div className="mb-10">
          <OfflineNotice />
        </div>
      ) : null}

      <SectionHead label={`${list.length} registered`} title="Player pool" action={invite} />

      {list.length === 0 ? (
        online ? (
          <EmptyState
            title="Nobody has signed up yet"
            detail="Profiles appear here as soon as players sign in and fill in their card."
          />
        ) : null
      ) : (
        <PlayerPool profiles={list} agents={agents ?? []} ranks={ranks ?? []} isAdmin={isAdmin} />
      )}
    </div>
  );
}
