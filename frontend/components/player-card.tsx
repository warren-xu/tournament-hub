"use client";

import Image from "next/image";
import type { AgentView, NerfTier, PlayerCardView, RankView } from "@/lib/types";
import { NERFS, TIER_COLOR } from "@/lib/nerfs";
import { RankPortrait } from "@/components/rank-portrait";
import { agentRoleColor, rankStyle } from "@/lib/rank-style";

/**
 * A full-card rank-bordered portrait with the player's identity.
 * - `stacked` (the profile page's preview): rank and profile details sit under the name.
 * - `split` (a player opened from the pool): the card stays on the left and a stats panel,
 *   peak rank included, sits to its right.
 */
export function PlayerCard({
  username,
  riotId,
  mainAgent,
  currentRank,
  peakRank = null,
  agents,
  ranks,
  playerCard = null,
  primaryRole = null,
  secondaryRole = null,
  agentPool = [],
  bio = null,
  bannerUrl = null,
  avatarUrl = null,
  bannerColor = null,
  layout = "stacked",
  nerfTier = null,
}: {
  username: string;
  riotId: string | null;
  mainAgent: string | null;
  currentRank: string | null;
  peakRank?: string | null;
  agents: AgentView[];
  ranks: RankView[];
  playerCard?: PlayerCardView | null;
  primaryRole?: string | null;
  secondaryRole?: string | null;
  agentPool?: string[];
  bio?: string | null;
  bannerUrl?: string | null;
  /** Discord avatar: behind the agent when no Valorant card art is picked. */
  avatarUrl?: string | null;
  /** Discord banner colour (0xRRGGBB), which non-Nitro accounts set instead of an image. */
  bannerColor?: number | null;
  layout?: "stacked" | "split";
  /** Shown as just "Tier 1" / "Tier 2" under the name. */
  nerfTier?: NerfTier | null;
}) {
  const agent = agents.find((a) => a.name.toLowerCase() === mainAgent?.toLowerCase());
  const rank = ranks.find((r) => r.name.toLowerCase() === currentRank?.toLowerCase());
  const accent = rankStyle(currentRank).color;
  const hasMeta = Boolean(primaryRole || secondaryRole || agentPool.length > 0 || bio);
  const split = layout === "split";

  const card = (
    <div className="min-w-0">
      <RankPortrait rank={currentRank} agent={agent} art={playerCard?.largeArt} avatarUrl={avatarUrl} name={mainAgent || username} shape="card" />
      <div className="showcase-identity relative z-20 mt-auto px-4 pb-6 pt-5">
        <p className="font-display text-[0.625rem] font-semibold uppercase tracking-[0.18em]" style={{ color: agentRoleColor(primaryRole ?? undefined) }}>
          {primaryRole ?? ""}
        </p>
        <h3 className="mt-1 break-words font-display text-2xl uppercase leading-none">
          {username}
        </h3>
        {nerfTier ? (
          // Hovering or focusing the badge spells out what the tier means.
          <p className="tier-tip mt-2">
            <span tabIndex={0} aria-describedby="tier-tip-rules"
              className={`inline-block cursor-help border px-1.5 py-0.5 font-display text-xs font-semibold uppercase tracking-wider ${TIER_COLOR[nerfTier]}`}>
              {NERFS[nerfTier].label}
            </span>
            <span id="tier-tip-rules" role="tooltip" className="tier-tip-body">
              {NERFS[nerfTier].rules.join(" · ")}
            </span>
          </p>
        ) : null}
        {split ? null : (
          <>
            <p className="mt-3 flex items-center gap-2 text-sm" style={{ color: accent }}>
              {rank?.iconUrl ? <Image src={rank.iconUrl} alt="" width={24} height={24} unoptimized /> : null}
              {currentRank || "Unranked"}
            </p>
            {peakRank && peakRank !== currentRank ? (
              <p className="mt-1 text-xs text-muted">Peak {peakRank}</p>
            ) : null}
          </>
        )}
        {riotId?.trim() ? (
          <p className="mt-1 break-all font-mono text-xs text-muted">
            {riotId.trim()}
          </p>
        ) : null}

        {!split && hasMeta ? (
          <div className="mt-4 space-y-3 border-t border-line-soft/60 pt-3">
            {primaryRole || secondaryRole ? (
              <p className="font-display text-xs uppercase tracking-widest text-muted">
                {primaryRole ?? "—"}
                <span className="mx-2 text-dim">/</span>
                {secondaryRole ?? "—"}
              </p>
            ) : null}
            <AgentPool names={agentPool} agents={agents} size={20} />
            {bio ? (
              <p className="text-sm leading-relaxed text-muted">{bio}</p>
            ) : null}
          </div>
        ) : null}
      </div>
    </div>
  );

  if (!split) {
    return (
      <article aria-label={`${username}'s player card`} className="player-showcase">
        {card}
      </article>
    );
  }

  return (
    <article aria-label={`${username}'s player card`} className="player-showcase player-showcase-split">
      {card}
      <section aria-label="Stats" className="showcase-stats">
        {/* The Discord banner: the image if they have one, else the banner colour they
            picked, else the plain panel. */}
        {bannerUrl ? (
          <span aria-hidden className="showcase-stats-backdrop" style={{ backgroundImage: `url("${bannerUrl}")` }} />
        ) : bannerColor !== null ? (
          <span aria-hidden className="showcase-stats-backdrop" data-color
            style={{ backgroundColor: `#${bannerColor.toString(16).padStart(6, "0")}` }} />
        ) : null}
        <dl className="relative space-y-5">
          <Stat label="Current rank">
            <RankValue name={currentRank} ranks={ranks} fallback="Unranked" />
          </Stat>
          <Stat label="Peak rank">
            <RankValue name={peakRank} ranks={ranks} fallback="—" />
          </Stat>
          <Stat label="Main agent">
            {agent ? (
              <span className="flex items-center gap-2">
                {agent.iconUrl ? (
                  <Image src={agent.iconUrl} alt="" width={28} height={28} unoptimized className="size-7 border border-line bg-ink" />
                ) : null}
                <span>{agent.name}</span>
              </span>
            ) : mainAgent || "—"}
          </Stat>
          <Stat label="Roles">
            {primaryRole || secondaryRole ? (
              <>
                {primaryRole ?? "—"}
                <span className="mx-2 text-dim">/</span>
                {secondaryRole ?? "—"}
              </>
            ) : "—"}
          </Stat>
          <Stat label="Agent pool">
            {agentPool.length > 0 ? <AgentPool names={agentPool} agents={agents} size={28} /> : "—"}
          </Stat>
          {bio ? (
            <Stat label="Notes for captains">
              <span className="block whitespace-pre-line leading-relaxed text-muted">{bio}</span>
            </Stat>
          ) : null}
        </dl>
      </section>
    </article>
  );
}

function Stat({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="eyebrow">{label}</dt>
      <dd className="mt-1.5 text-sm text-bone">{children}</dd>
    </div>
  );
}

function RankValue({ name, ranks, fallback }: { name: string | null; ranks: RankView[]; fallback: string }) {
  if (!name) return <>{fallback}</>;
  const rank = ranks.find((r) => r.name.toLowerCase() === name.toLowerCase());
  return (
    <span className="flex items-center gap-2" style={{ color: rankStyle(name).color }}>
      {rank?.iconUrl ? <Image src={rank.iconUrl} alt="" width={28} height={28} unoptimized /> : null}
      {name}
    </span>
  );
}

function AgentPool({ names, agents, size }: { names: string[]; agents: AgentView[]; size: number }) {
  if (names.length === 0) return null;
  return (
    <ul className="flex flex-wrap gap-1">
      {names.map((name) => {
        const pooled = agents.find((a) => a.name === name);
        return (
          <li key={name} title={name}>
            {pooled?.iconUrl ? (
              <Image
                src={pooled.iconUrl}
                alt={name}
                width={size}
                height={size}
                unoptimized
                className="border border-line bg-ink/60"
                style={{ width: size, height: size }}
              />
            ) : (
              <span className="border border-line px-1.5 py-0.5 font-display text-[0.625rem] uppercase tracking-widest text-muted">
                {name}
              </span>
            )}
          </li>
        );
      })}
    </ul>
  );
}
