import { ProfileForm } from "./profile-form";
import { Eyebrow, LinkButton, OfflineNotice } from "@/components/ui";
import {
  backendReachable,
  getAgents,
  getMe,
  getMyProfile,
  getRanks,
  getPlayerCards,
} from "@/lib/server-api";

export const metadata = { title: "My profile - Warrenament" };

export default async function ProfilePage() {
  const online = await backendReachable();
  const me = await getMe();

  if (!online) {
    return (
      <div className="mx-auto max-w-[1400px] px-6 py-12">
        <OfflineNotice />
      </div>
    );
  }

  if (!me) {
    return (
      <div className="mx-auto max-w-[1400px] px-6 py-20">
        <div className="max-w-md">
          <Eyebrow>Not signed in</Eyebrow>
          <h1 className="mt-3 text-4xl uppercase leading-none tracking-tight">
            Your player card
          </h1>
          <p className="mt-4 text-sm leading-relaxed text-muted">
            Captains read this card while they decide what you are worth. Sign in
            to fill it out.
          </p>
          <div className="mt-8">
            <LinkButton href="/signin" tone="primary">
              Sign in
            </LinkButton>
          </div>
        </div>
      </div>
    );
  }

  const [profile, agents, ranks, playerCards] = await Promise.all([
    getMyProfile(),
    getAgents(),
    getRanks(),
    getPlayerCards(),
  ]);

  return (
    <div className="mx-auto max-w-[1400px] px-6 py-12">
      <div className="flex items-center gap-3">
        <span aria-hidden className="block h-px w-10 bg-accent" />
        <Eyebrow>Signed in as {me.username}</Eyebrow>
      </div>
      <h1 className="mt-4 text-4xl uppercase leading-none tracking-tight">
        Your player card
      </h1>
      <p className="mt-4 max-w-lg text-sm leading-relaxed text-muted">
        This is what captains see when your name comes up on the auction block.
        The more of it you fill in, the less they are guessing.
      </p>

      <ProfileForm
        username={me.username}
        initial={profile}
        agents={agents ?? []}
        ranks={ranks ?? []}
        playerCards={playerCards ?? []}
      />
    </div>
  );
}
