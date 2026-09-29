import { notFound } from "next/navigation";
import { SignUp } from "./sign-up";
import { CalendarLinks } from "./calendar-links";
import { DraftPool } from "./draft-pool";
import { TeamName } from "./team-name";
import { DeleteTeam } from "./delete-team";
import { AuctionSetup } from "./auction-setup";
import {
  EmptyState,
  Eyebrow,
  LinkButton,
  OfflineNotice,
  SectionHead,
} from "@/components/ui";
import { LocalTime } from "@/components/local-time";
import { tournamentEvent } from "@/lib/calendar";
import { siteOrigin } from "@/lib/site-origin";
import {
  backendReachable,
  getAgents,
  getAuctionByTournament,
  getMe,
  getMyProfile,
  getProfiles,
  getRanks,
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

  const [me, myProfile, registrations, teams, auction, agents, ranks, origin, profiles] = await Promise.all([
    getMe(),
    getMyProfile(),
    getRegistrations(tournament.id),
    getTeams(tournament.id),
    getAuctionByTournament(tournament.id),
    getAgents(),
    getRanks(),
    siteOrigin(),
    getProfiles(),
  ]);

  const pool = registrations ?? [];
  const approved = pool.filter((r) => r.status === "APPROVED");
  const roster = teams ?? [];
  const isAdmin = me?.role === "ADMIN";
  const myRegistration = myProfile
    ? pool.find((r) => r.player.id === myProfile.id)
    : undefined;
  const event = tournamentEvent(tournament, origin);
  // Players see who signed up (they stay listed once drafted). Admins see every player,
  // so they can add anyone to the queue by hand.
  const allPlayers = profiles ?? [];
  // Rosters list profiles and teams name their captain by user, so match the two up.
  const captainUserIds = new Set(roster.map((team) => team.captainUserId));
  const captainProfileIds = new Set(
    allPlayers.filter((p) => captainUserIds.has(p.userId)).map((p) => p.id),
  );
  const poolPlayers = isAdmin ? allPlayers : approved.map((r) => r.player);

  // The pool and teams can change until the draft starts.
  const settingUp = !auction || auction.status === "SETUP";
  // People already on a team (the captains, before the draft), for the admin's split readout.
  const seated = roster.reduce((n, team) => n + team.roster.length, 0);

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
              <dt>Starts</dt>
              <dd className="text-bone">
                {tournament.startsAt ? <LocalTime iso={tournament.startsAt} /> : "To be announced"}
              </dd>
            </div>
          </dl>
          {event && tournament.status !== "COMPLETE" ? (
            <div className="mt-5">
              <CalendarLinks event={event} slug={slug} />
            </div>
          ) : null}
        </div>

        {auction ? (
          <LinkButton href={`/t/${slug}/draft`} tone="primary">
            {auction.status === "LIVE" ? "Enter draft room" : "Draft room"}
          </LinkButton>
        ) : null}
      </div>

      {isAdmin ? (
        <div className="mt-8">
          <AuctionSetup
            tournamentId={tournament.id}
            tournamentStatus={tournament.status}
            startsAt={tournament.startsAt}
            auction={auction}
            approvedCount={approved.length}
            teamCount={roster.length}
            seatedCount={seated}
          />
        </div>
      ) : null}

      <section className="pt-12">
        <SectionHead
          label={`${roster.length} teams`}
          title="Rosters"
        />

        {roster.length === 0 ? (
          <EmptyState
            title="No teams yet"
            detail="An admin adds a team for each captain before the draft starts. Captains leave the queue and lead their team."
          />
        ) : (
          <ul className="grid gap-px border border-line bg-line md:grid-cols-2 xl:grid-cols-3">
            {roster.map((team) => {
              return (
                <li key={team.id}>
                  <article className="flex h-full flex-col bg-panel p-5">
                    <div className="flex items-baseline justify-between gap-3">
                      <TeamName
                        teamId={team.id}
                        name={team.name}
                        logoUrl={team.logoUrl}
                        editable={isAdmin || me?.userId === team.captainUserId}
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
                          {captainProfileIds.has(member.profileId) ? (
                            <span className="shrink-0 text-xs uppercase tracking-wider text-dim">Captain</span>
                          ) : (
                            <span className="tabular shrink-0 text-muted">
                              {member.pricePaid}
                            </span>
                          )}
                        </li>
                      ))}
                    </ul>
                    {isAdmin && settingUp ? (
                      <div className="mt-auto">
                        <DeleteTeam teamId={team.id} name={team.name} />
                      </div>
                    ) : null}
                  </article>
                </li>
              );
            })}
          </ul>
        )}
      </section>

      <section className="pt-12">
        {tournament.status === "REGISTRATION" ? (
          <div className="mb-8">
            <SignUp
              tournament={tournament}
              signedIn={Boolean(me)}
              profile={myProfile}
              signedUp={myRegistration?.status === "APPROVED"}
              locked={!settingUp}
              event={event}
              agents={agents ?? []}
              ranks={ranks ?? []}
            />
          </div>
        ) : null}
        <SectionHead
          label={isAdmin
            ? `${approved.length} signed up · ${allPlayers.length} players`
            : `${approved.length} signed up`}
          title="Draft pool"
        />
        {poolPlayers.length === 0 ? (
          <EmptyState
            title={isAdmin ? "No players yet" : "Nobody has signed up yet"}
            detail={isAdmin
              ? "Players appear here once they sign in and create a profile."
              : tournament.status === "REGISTRATION"
                ? "Sign up above to be the first in the queue."
                : "Sign-ups are not open for this tournament."}
          />
        ) : (
          <DraftPool
            tournamentId={tournament.id}
            profiles={poolPlayers}
            queuedIds={approved.map((r) => r.player.id)}
            captainUserIds={roster.map((team) => team.captainUserId)}
            onTeamIds={roster.flatMap((team) => team.roster.map((m) => m.profileId))}
            isAdmin={isAdmin}
            editable={settingUp}
          />
        )}
      </section>
    </div>
  );
}
