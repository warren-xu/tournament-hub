"use client";

import Image from "next/image";
import { PlayerCardPicker } from "@/components/player-card-picker";
import { useState } from "react";
import { buttonClass, Eyebrow } from "@/components/ui";
import { api, ApiCallError } from "@/lib/client-api";
import type { AgentView, RankView, SyncResult, PlayerCardView } from "@/lib/types";
import { groupByDivision, groupByRole, rankColor } from "@/lib/valorant";

type Section = "agents" | "ranks" | "player-cards";

/**
 * Read-only view of the synced reference data, plus the one action that changes it.
 * The lists are here so you can see what the sync produced, not to be edited by hand.
 */
export function GameDataAdmin({
  initialAgents,
  initialRanks,
  initialCards,
  sources,
}: {
  initialAgents: AgentView[];
  initialRanks: RankView[];
  initialCards: PlayerCardView[];
  sources: Record<Section, string>;
}) {
  const [agents, setAgents] = useState(initialAgents);
  const [ranks, setRanks] = useState(initialRanks);
  const [cards, setCards] = useState(initialCards);
  const [previewCard, setPreviewCard] = useState<PlayerCardView | null>(null);
  const [syncing, setSyncing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [results, setResults] = useState<Partial<Record<Section, SyncResult>>>({});

  async function syncAll(only?: Section) {
    setSyncing(true);
    setError(null);
    try {
      const next: Partial<Record<Section, SyncResult>> = {};
      const sections: Section[] = only ? [only] : ["agents", "ranks", "player-cards"];
      const failures: string[] = [];

      // Settled, not all: one source failing should not hide the other's result.
      const outcomes = await Promise.allSettled(
        sections.map((section) => api<SyncResult>(`/api/${section}/sync`, { method: "POST" })),
      );

      outcomes.forEach((outcome, i) => {
        if (outcome.status === "fulfilled") {
          next[sections[i]] = outcome.value;
        } else {
          failures.push(
            `${sections[i]}: ${
              outcome.reason instanceof ApiCallError
                ? outcome.reason.message
                : "sync failed"
            }`,
          );
        }
      });

      setResults(next);

      const [a, r, c] = await Promise.all([
        api<AgentView[]>("/api/agents"),
        api<RankView[]>("/api/ranks"),
        api<PlayerCardView[]>("/api/player-cards"),
      ]);
      setAgents(a);
      setRanks(r);
      setCards(c);

      if (failures.length > 0) setError(failures.join(" · "));
    } catch (err) {
      setError(err instanceof ApiCallError ? err.message : "The sync did not work.");
    } finally {
      setSyncing(false);
    }
  }

  return (
    <div className="grid gap-8 lg:grid-cols-[1fr_320px]">
      <div className="space-y-12">
        {error ? (
          <p
            role="alert"
            className="border border-accent-deep bg-accent-deep/10 px-4 py-2 text-sm text-signal"
          >
            {error}
          </p>
        ) : null}

        <section>
          <SectionTitle title="Competitive ranks" count={`${ranks.length} tiers`} />

          {ranks.length === 0 ? (
            <Empty what="ranks" />
          ) : (
            <div className="space-y-4">
              {groupByDivision(ranks).map(([division, tiers]) => (
                <div key={division}>
                  <p className="mb-2 font-display text-xs uppercase tracking-widest text-muted">
                    {division}
                  </p>
                  <ul className="flex flex-wrap gap-2">
                    {tiers.map((tier) => (
                      <li
                        key={tier.id}
                        className="flex items-center gap-2 border border-line bg-panel px-2 py-1.5"
                        style={
                          tier.color ? { borderColor: rankColor(tier.color) } : undefined
                        }
                      >
                        {tier.iconUrl ? (
                          <Image
                            src={tier.iconUrl}
                            alt=""
                            width={24}
                            height={24}
                            unoptimized
                            className="size-6"
                          />
                        ) : (
                          <span className="size-6 bg-raise" />
                        )}
                        <span className="font-display text-sm uppercase tracking-wide text-bone">
                          {tier.name}
                        </span>
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          )}
        </section>

        <section>
          <SectionTitle title="Agents" count={`${agents.length} agents`} />

          {agents.length === 0 ? (
            <Empty what="agents" />
          ) : (
            <div className="space-y-6">
              {groupByRole(agents).map(([role, roleAgents]) => (
                <div key={role}>
                  <p className="mb-2 font-display text-xs uppercase tracking-widest text-muted">
                    {role}
                  </p>
                  <ul className="flex flex-wrap gap-2">
                    {roleAgents.map((agent) => (
                      <li
                        key={agent.id}
                        className="flex items-center gap-2 border border-line bg-panel py-1 pr-2.5 pl-1"
                      >
                        {agent.iconUrl ? (
                          <Image
                            src={agent.iconUrl}
                            alt=""
                            width={28}
                            height={28}
                            unoptimized
                            className="size-7 bg-raise"
                          />
                        ) : (
                          <span className="size-7 bg-raise" />
                        )}
                        <span className="font-display text-sm uppercase tracking-wide text-bone">
                          {agent.name}
                        </span>
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          )}
        </section>
        <section>
          <SectionTitle title="Player cards" count={`${cards.length} cards`} />
          <button type="button" onClick={() => syncAll("player-cards")} disabled={syncing} className={buttonClass("default", "mb-4")}>
            {syncing ? "Syncing…" : "Sync player cards"}
          </button>
          <PlayerCardPicker cards={cards} selected={previewCard} onChange={setPreviewCard} />
        </section>
      </div>

      <aside className="lg:sticky lg:top-20 lg:self-start">
        <div className="border border-line bg-panel p-5">
          <Eyebrow>Sync</Eyebrow>
          <ul className="mt-2 space-y-0.5">
            {(Object.keys(sources) as Section[]).map((key) => (
              <li key={key} className="break-all font-mono text-[0.6875rem] text-dim">
                {sources[key]}
              </li>
            ))}
          </ul>

          <button
            onClick={() => syncAll()}
            disabled={syncing}
            className={buttonClass("primary", "mt-4 w-full")}
          >
            {syncing ? "Syncing…" : "Sync from valorant-api"}
          </button>

          <p className="mt-3 text-xs leading-relaxed text-dim">
            Adds new agents, tiers and player cards, and refreshes their names and artwork.
            Nothing is ever deleted.
          </p>

          {(Object.keys(results) as Section[]).map((key) => {
            const result = results[key];
            if (!result) return null;
            return (
              <dl
                key={key}
                aria-live="polite"
                className="tabular mt-4 border-t border-line-soft pt-4 text-sm"
              >
                <p className="eyebrow mb-1">{key}</p>
                {(
                  [
                    ["Added", result.added],
                    ["Updated", result.updated],
                    ["Unchanged", result.unchanged],
                  ] as const
                ).map(([label, value]) => (
                  <div key={label} className="flex justify-between">
                    <dt className="text-dim">{label}</dt>
                    <dd className={value > 0 ? "text-signal" : "text-muted"}>{value}</dd>
                  </div>
                ))}
                {result.note ? (
                  <p className="pt-1 text-xs text-signal">{result.note}</p>
                ) : null}
                {result.notInSource.length > 0 ? (
                  <p className="pt-1 text-xs leading-relaxed text-dim">
                    Not in the source, kept anyway:{" "}
                    <span className="text-muted">{result.notInSource.join(", ")}</span>
                  </p>
                ) : null}
              </dl>
            );
          })}
        </div>
      </aside>
    </div>
  );
}

function SectionTitle({ title, count }: { title: string; count: string }) {
  return (
    <div className="mb-4 flex items-baseline justify-between border-b border-line-soft pb-2">
      <h2 className="font-display text-xl uppercase tracking-wide">{title}</h2>
      <span className="tabular text-xs text-dim">{count}</span>
    </div>
  );
}

function Empty({ what }: { what: string }) {
  return (
    <div className="border border-dashed border-line px-6 py-10 text-center">
      <p className="text-sm text-dim">
        No {what} yet — pull them with the sync button.
      </p>
    </div>
  );
}
