import { getMe, getTournaments } from "@/lib/server-api";
import { Eyebrow, LinkButton, Tag } from "@/components/ui";
import { DeleteTournament } from "./delete-tournament";

export const metadata = { title: "Admin — Warrenament" };

export default async function AdminPage() {
  const me = await getMe();
  if (me?.role !== "ADMIN") return <div className="mx-auto max-w-3xl px-6 py-16"><h1 className="text-3xl">Admin access required</h1><p className="my-4 text-muted">Sign in with an admin account to manage tournaments.</p><LinkButton href="/signin">Sign in</LinkButton></div>;
  const tournaments = await getTournaments();
  const status = { DRAFT: "Setting up", REGISTRATION: "Sign-ups open", DRAFTING: "Drafting", LIVE: "Rosters locked", COMPLETE: "Finished" };
  return <div className="mx-auto max-w-5xl px-6 py-12">
    <Eyebrow>Admin workspace</Eyebrow><h1 className="mt-2 text-4xl">Tournament management</h1>
    <p className="mt-3 text-muted">Create an event, choose your captains, and bring everyone together.</p>
    <div className="my-8 flex flex-wrap gap-3"><LinkButton href="/admin/tournaments" tone="primary">Create tournament →</LinkButton><LinkButton href="/admin/game-data">Manage game data</LinkButton></div>
    <h2 className="mb-4 text-xl">Your tournaments</h2>
    {tournaments === null ? <p role="alert">We couldn’t load tournaments. Please refresh to try again.</p> : tournaments.length === 0 ? <p className="rounded-xl border border-dashed border-line p-8 text-muted">No tournaments yet. Start with “Create tournament” above.</p> : <ul className="space-y-3">{tournaments.map(t => <li key={t.id} className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-line bg-panel p-5"><div><h3 className="text-xl">{t.name}</h3><p className="mt-1 text-sm text-muted">{t.rosterSize} people per team · {t.creditBudget} starting credits</p></div><Tag>{status[t.status]}</Tag><div className="flex items-center gap-4"><LinkButton href={`/t/${t.slug}`}>Manage event →</LinkButton><DeleteTournament tournamentId={t.id} name={t.name} /></div></li>)}</ul>}
  </div>;
}
