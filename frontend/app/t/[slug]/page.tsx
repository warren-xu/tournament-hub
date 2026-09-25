import Link from "next/link";
import { notFound } from "next/navigation";
import { RegisterButton } from "./register-button";
import { PoolAdmin } from "./pool-admin";
import { AuctionSetup } from "./auction-setup";
import {
  Avatar,
  EmptyState,
  Eyebrow,
  LinkButton,
  OfflineNotice,
  SectionHead,
} from "@/components/ui";
import {
  backendReachable,
  getAuctionByTournament,
  getMe,
  getMyProfile,
  getRegistrations,
  getTeams,
  getTournamentBySlug,
} from "@/lib/server-api";
import type { TournamentStatus } from "@/lib/types";

const STATUS_COPY: Record<TournamentStatus, string> = {
  DRAFT: "Setting up",
  REGISTRATION: "Sign-ups open",
  DRAFTING: "Draft in progress",
  LIVE: "Rosters locked",
  COMPLETE: "Finished",
};

export default async function TournamentPage(props: PageProps<"/t/[slug]">) {
  const { slug } = await props.params;

  if (!(await backendReachable())) {
    return (
      <div className="mx-auto max-w-[1400px] px-6 py-12">
        <OfflineNotice />
      </div>
    );
  }

  const tournament = await getTournamentBySlug(slug);
  if (!tournament) notFound();

  const [me, myProfile, registrations, teams, auction] = await Promise.all([
    getMe(),
    getMyProfile(),
    getRegistrations(tournament.id),
    getTeams(tournament.id),
    getAuctionByTournament(tournament.id),
  ]);

  const pool = registrations ?? [];
  const approved = pool.filter((r) => r.status === "APPROVED");
  const pending = pool.filter((r) => r.status === "PENDING");
  const roster = teams ?? [];
  const isAdmin = me?.role === "ADMIN";
  const myRegistration = myProfile
    ? pool.find((r) => r.player.id === myProfile.id)
    : undefined;

  return (
    <div className="mx-auto max-w-[1400px] px-6 py-12">
      <div className="flex flex-wrap items-start justify-between gap-6 border-b border-line-soft pb-8">
        <div>
          <div className="flex items-center gap-3">
            <span aria-hidden className="block h-px w-10 bg-accent" />
            <Eyebrow>{STATUS_COPY[tournament.status]}</Eyebrow>
          </div>
          <h1 className="mt-4 text-5xl uppercase leading-none tracking-tight">
            {tournament.name}
          </h1>
          <dl className="tabular mt-5 flex flex-wrap gap-x-8 gap-y-2 text-sm text-muted">
            <div className="flex gap-2">
              <dt>Budget</dt>
              <dd className="text-bone">{tournament.creditBudget} credits</dd>
            </div>
            <div className="flex gap-2">
              <dt>Roster</dt>
              <dd className="text-bone">{tournament.rosterSize} players</dd>
            </div>
            <div className="flex gap-2">
              <dt>Min bid</dt>
              <dd className="text-bone">{tournament.minBid}</dd>
            </div>
            <div className="flex gap-2">
              <dt>Bidding</dt>
              <dd className="text-bone">Sealed</dd>
            </div>
          </dl>
        </div>

        <div className="flex flex-col items-start gap-3">
          {auction ? (
            <LinkButton href={`/t/${slug}/draft`} tone="primary">
              {auction.status === "LIVE" ? "Enter the draft" : "Draft room"}
            </LinkButton>
          ) : null}
          {tournament.status === "REGISTRATION" ? (
            <RegisterButton
              tournamentId={tournament.id}
              signedIn={Boolean(me)}
              registered={Boolean(myRegistration)}
              status={myRegistration?.status ?? null}
            />
          ) : null}
        </div>
      </div>

      {isAdmin ? (
        <div className="mt-8">
          <AuctionSetup
            tournamentId={tournament.id}
            tournamentStatus={tournament.status}
            auction={auction}
            approvedCount={approved.length}
            teamCount={roster.length}
          />
        </div>
      ) : null}

      <section className="pt-12">
        <SectionHead
          label={`${roster.length} teams`}
          title="Rosters"
          action={
            auction ? (
              <Link
                href={`/t/${slug}/draft`}
                className="font-display text-sm uppercase tracking-wider text-dim transition-colors hover:text-bone"
              >
                Watch the draft →
              </Link>
            ) : null
          }
        />

        {roster.length === 0 ? (
          <EmptyState
            title="No teams yet"
            detail="An admin creates a team for each captain before the draft can start."
          />
        ) : (
          <ul className="grid gap-px border border-line bg-line md:grid-cols-2 xl:grid-cols-3">
            {roster.map((team) => {
              const spent = tournament.creditBudget - team.remainingCredits;
              const pct = Math.round((spent / tournament.creditBudget) * 100);
              return (
                <li key={team.id}>
                  <article className="flex h-full flex-col bg-panel p-5">
                    <div className="flex items-baseline justify-between gap-3">
                      <h3 className="font-display text-xl uppercase tracking-wide">
                        {team.name}
                      </h3>
                      <p className="tabular font-display text-lg font-semibold text-signal">
                        {team.remainingCredits}
                        <span className="ml-1 text-xs text-dim">left</span>
                      </p>
                    </div>

                    <div
                      className="mt-3 h-1 w-full bg-raise"
                      role="img"
                      aria-label={`${spent} of ${tournament.creditBudget} credits spent`}
                    >
                      <div
                        className="h-full bg-accent-deep"
                        style={{ width: `${pct}%` }}
                      />
                    </div>

                    <ul className="mt-4 space-y-1.5 text-sm">
                      {team.roster.map((member) => (
                        <li
                          key={member.id}
                          className="flex items-baseline justify-between gap-3 border-b border-line-soft pb-1.5"
                        >
                          <span className="truncate text-bone">
                            {member.username}
                          </span>
                          <span className="tabular shrink-0 text-muted">
                            {member.pricePaid}
                          </span>
                        </li>
                      ))}
                      {Array.from({
                        length: Math.max(
                          0,
                          tournament.rosterSize - team.roster.length,
                        ),
                      }).map((_, i) => (
                        <li
                          key={`empty-${i}`}
                          className="border-b border-dashed border-line-soft pb-1.5 text-dim"
                        >
                          Open slot
                        </li>
                      ))}
                    </ul>
                  </article>
                </li>
              );
            })}
          </ul>
        )}
      </section>

      <section className="pt-12">
        <SectionHead
          label={`${approved.length} approved · ${pending.length} pending`}
          title="Draft pool"
        />

        {pool.length === 0 ? (
          <EmptyState
            title="Nobody has registered"
            detail={
              tournament.status === "REGISTRATION"
                ? "Players register from this page once they have filled in their card."
                : "Registration is not open for this tournament."
            }
          />
        ) : isAdmin ? (
          <PoolAdmin registrations={pool} />
        ) : (
          <ul className="grid gap-px border border-line bg-line sm:grid-cols-2 lg:grid-cols-4">
            {approved.map((r) => (
              <li key={r.id} className="flex items-center gap-3 bg-panel p-4">
                <Avatar src={r.player.avatarUrl} name={r.player.username} />
                <div className="min-w-0">
                  <p className="truncate text-sm text-bone">
                    {r.player.username}
                  </p>
                  <p className="truncate text-xs text-dim">
                    {r.player.primaryRole ?? "role unset"}
                  </p>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
