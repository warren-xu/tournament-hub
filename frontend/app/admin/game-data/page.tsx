import { GameDataAdmin } from "./game-data-admin";
import { Eyebrow, LinkButton, OfflineNotice } from "@/components/ui";
import {
  backendReachable,
  getAgents,
  getMe,
  getRanks,
  getPlayerCards,
} from "@/lib/server-api";

export const metadata = { title: "Game data — Warrenament" };

const SOURCES = {
  agents: "https://valorant-api.com/v1/agents",
  ranks: "https://valorant-api.com/v1/competitivetiers",
  "player-cards": "https://valorant-api.com/v1/playercards",
};

export default async function GameDataPage() {
  if (!(await backendReachable())) {
    return (
      <div className="mx-auto max-w-[1400px] px-6 py-12">
        <OfflineNotice />
      </div>
    );
  }

  const me = await getMe();

  if (me?.role !== "ADMIN") {
    return (
      <div className="mx-auto max-w-[1400px] px-6 py-20">
        <div className="max-w-md">
          <Eyebrow>Admins only</Eyebrow>
          <h1 className="mt-3 text-4xl uppercase leading-none tracking-tight">
            Game data
          </h1>
          <p className="mt-4 text-sm leading-relaxed text-muted">
            {me
              ? "Your account is not an admin, so you cannot change the game data lists."
              : "Sign in with an admin account to change the game data lists."}
          </p>
          {!me ? (
            <div className="mt-8">
              <LinkButton href="/signin" tone="primary">
                Sign in
              </LinkButton>
            </div>
          ) : null}
        </div>
      </div>
    );
  }

  const [agents, ranks, cards] = await Promise.all([getAgents(), getRanks(), getPlayerCards()]);

  return (
    <div className="mx-auto max-w-[1400px] px-6 py-12">
      <div className="max-w-3xl">
        <div className="flex items-center gap-3">
          <span aria-hidden className="block h-px w-10 bg-accent" />
          <Eyebrow>Admin</Eyebrow>
        </div>
        <h1 className="mt-4 text-4xl uppercase leading-none tracking-tight">
          Game data
        </h1>
        <p className="mt-4 max-w-xl text-sm leading-relaxed text-muted">
          Agents, competitive ranks and player cards, pulled from valorant-api.com rather than
          maintained by hand. This is what players choose from on their card.
        </p>
      </div>

      <div className="mt-8">
        <GameDataAdmin
          initialAgents={agents ?? []}
          initialRanks={ranks ?? []}
          initialCards={cards ?? []}
          sources={SOURCES}
        />
      </div>
    </div>
  );
}
