import { getMe, getProfiles } from "@/lib/server-api";
import { LinkButton } from "@/components/ui";
import { TournamentCreator } from "./tournament-creator";

export const metadata = { title: "Create tournament — Warrenament" };

export default async function CreateTournamentPage() {
  const me = await getMe();
  if (me?.role !== "ADMIN") return <div className="mx-auto max-w-3xl px-6 py-16"><h1 className="text-3xl">Admin access required</h1><p className="my-4 text-muted">Sign in with an admin account to create a tournament.</p><LinkButton href="/signin">Sign in</LinkButton></div>;
  const profiles = await getProfiles();
  return <div className="mx-auto max-w-5xl px-6 py-12"><LinkButton href="/admin" tone="ghost">← Admin home</LinkButton>
    <h1 className="mt-6 text-4xl">Create a tournament</h1><p className="mt-3 mb-8 text-muted">Choose the captains. Players join the draft pool by signing up, or you add them by hand.</p>
    {profiles === null ? <p role="alert">We couldn’t load the players. Refresh the page to try again.</p> : profiles.length < 2 ? <div className="rounded-xl border border-line p-6"><p>You need at least two player profiles to captain two teams.</p><p className="my-3 text-muted">Ask participants to sign in and create their profile first.</p><LinkButton href="/">View players</LinkButton></div> : <TournamentCreator profiles={profiles} />}
  </div>;
}
