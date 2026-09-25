import { EmptyState, SectionHead } from "@/components/ui";
import { PlayerPool } from "@/components/player-pool";
import { getAgents, getMe, getProfiles, getRanks } from "@/lib/server-api";

export const metadata = { title: "Players — Warrenament" };

export default async function PlayersPage() {
  const [profiles, ranks, agents, me] = await Promise.all([
    getProfiles(),
    getRanks(true),
    getAgents(true),
    getMe(),
  ]);

  const list = profiles ?? [];
  const isAdmin = me?.role === "ADMIN";
  const carded = list.filter((p) => p.mainAgent || p.playerCard);

  return (
    <div className="mx-auto max-w-[1400px] px-6 py-12">
      <SectionHead
        label={`${list.length} registered`}
        title="Player pool"
        action={null
        }
      />

      {list.length === 0 ? (
        <EmptyState
          title="Nobody has signed up yet"
          detail="Profiles appear here as soon as players sign in and fill in their card."
        />
      ) : (
        <PlayerPool profiles={list} agents={agents ?? []} ranks={ranks ?? []} isAdmin={isAdmin} />
      )}
    </div>
  );
}
