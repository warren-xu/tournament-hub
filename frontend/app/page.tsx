import Link from "next/link";
import {
  EmptyState,
  Eyebrow,
  LinkButton,
  OfflineNotice,
  SectionHead,
  Tag,
} from "@/components/ui";
import {
  backendReachable,
  getProfiles,
  getTournaments,
} from "@/lib/server-api";
import type { TournamentStatus } from "@/lib/types";

const STATUS_COPY: Record<TournamentStatus, string> = {
  DRAFT: "Setting up",
  REGISTRATION: "Sign-ups open",
  DRAFTING: "Draft in progress",
  LIVE: "Rosters locked",
  COMPLETE: "Finished",
};

export default async function HomePage() {
  const [online, tournaments, profiles] = await Promise.all([
    backendReachable(),
    getTournaments(),
    getProfiles(),
  ]);

  const list = tournaments ?? [];
  const open = list.filter((t) => t.status === "REGISTRATION").length;

  return (
    <div className="mx-auto max-w-[1400px] px-4 py-8 sm:px-6 sm:py-12">
      {!online ? (
        <div className="mb-10">
          <OfflineNotice />
        </div>
      ) : null}

      {/* Asymmetric opener: statement left, hard numbers right. */}
      <section className="grid gap-10 border-b border-line-soft pb-12 lg:grid-cols-[1.6fr_1fr]">
        <div>
          <div className="flex items-center gap-3">
            <span aria-hidden className="block h-px w-10 bg-accent" />
            <Eyebrow>Valorant tournament hub</Eyebrow>
          </div>
          <h1 className="mt-4 max-w-2xl text-5xl uppercase leading-[0.95] tracking-tight sm:text-6xl">
            Every pick counts.
            <br />
            <span className="text-signal">Follow the draft.</span>
          </h1>
          <p className="mt-6 max-w-lg text-base leading-relaxed text-muted">
            Watch teams take shape, explore the players, and follow every reveal.
            Ready to compete? Create your profile and join an open tournament.
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <LinkButton href="#tournaments" tone="primary">
              Explore tournaments
            </LinkButton>
            <LinkButton href="/profile">Join as a player</LinkButton>
          </div>
        </div>

        <dl className="grid grid-cols-2 gap-px self-start border border-line bg-line lg:mt-14">
          {[
            { label: "Tournaments", value: list.length },
            { label: "Sign-ups open", value: open },
            { label: "Players", value: profiles?.length ?? 0 },
            { label: "Drafts in progress", value: list.filter((t) => t.status === "DRAFTING").length },
          ].map((stat) => (
            <div key={stat.label} className="bg-panel px-5 py-6">
              <dt className="eyebrow">{stat.label}</dt>
              <dd className="tabular mt-2 font-display text-3xl font-semibold">
                {stat.value}
              </dd>
            </div>
          ))}
        </dl>
      </section>

      <section id="tournaments" className="scroll-mt-36 pt-10">
        <SectionHead label="All events" title="Tournaments" />

        {list.length === 0 ? (
          <EmptyState
            title="No tournaments yet"
            detail="The next event will appear here. Explore the player pool while you wait."
            action={<LinkButton href="/players">Explore players</LinkButton>}
          />
        ) : (
          <ul className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {list.map((t) => (
              <li
                key={t.id}
                className="min-w-0"
              >
                <Link
                  href={`/t/${t.slug}`}
                  className="group flex h-full flex-col items-start gap-5 rounded-sm border border-line bg-panel p-6 transition-colors hover:border-muted hover:bg-raise"
                >
                  <span className="min-w-0">
                    <span className="block font-display text-xl uppercase tracking-wide transition-colors group-hover:text-signal">
                      {t.name}
                    </span>

                  </span>

                  <Tag tone={t.status === "DRAFTING" ? "accent" : "outline"}>
                    {STATUS_COPY[t.status]}
                  </Tag>

                  <span className="tabular flex gap-6 text-sm text-muted">
                    <span>
                      <span className="text-bone">{t.creditBudget}</span> credits
                    </span>
                    <span>
                      <span className="text-bone">{t.rosterSize}</span> per roster
                    </span>
                  </span>

                  <span
                    aria-hidden
                    className="mt-auto flex min-h-11 w-full items-center justify-between border-t border-line-soft pt-4 font-display text-sm uppercase tracking-wider text-bone"
                  >
                    {t.status === "DRAFTING" ? "Watch draft" : "View teams"} →
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
