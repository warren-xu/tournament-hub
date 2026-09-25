"use client";

import Image from "next/image";
import type { AgentView, PlayerCardView, RankView } from "@/lib/types";
import { RankPortrait } from "@/components/rank-portrait";
import { agentRoleColor, rankStyle } from "@/lib/rank-style";

/** A full-card rank-bordered portrait with a readable identity and profile details below. */
export function PlayerCard({
  username,
  riotId,
  mainAgent,
  currentRank,
  agents,
  ranks,
  playerCard = null,
  primaryRole = null,
  secondaryRole = null,
  agentPool = [],
  bio = null,
}: {
  username: string;
  riotId: string | null;
  mainAgent: string | null;
  currentRank: string | null;
  agents: AgentView[];
  ranks: RankView[];
  playerCard?: PlayerCardView | null;
  primaryRole?: string | null;
  secondaryRole?: string | null;
  agentPool?: string[];
  bio?: string | null;
}) {
  const agent = agents.find((a) => a.name.toLowerCase() === mainAgent?.toLowerCase());
  const rank = ranks.find((r) => r.name.toLowerCase() === currentRank?.toLowerCase());
  const accent = rankStyle(currentRank).color;
  const hasMeta = Boolean(primaryRole || secondaryRole || agentPool.length > 0 || bio);

  return (
    <article aria-label={`${username}'s player card`} className="player-showcase">
      <RankPortrait rank={currentRank} agent={agent} art={playerCard?.largeArt} name={mainAgent || username} shape="card" />
      {/* Layer 5 — identity. */}
      <div className="showcase-identity relative z-20 mt-auto px-4 pb-6 pt-5">
        <p className="font-display text-[0.625rem] font-semibold uppercase tracking-[0.18em]" style={{ color: agentRoleColor(agent?.role) }}>
          {agent ? `${agent.role}` : mainAgent || ""}
        </p>
        <h3 className="mt-1 break-words font-display text-2xl uppercase leading-none">
          {username}
        </h3>
        <p className="mt-3 flex items-center gap-2 text-sm" style={{ color: accent }}>
          {rank?.iconUrl ? <Image src={rank.iconUrl} alt="" width={24} height={24} unoptimized /> : null}
          {currentRank || "Unranked"}
        </p>
        {riotId?.trim() ? (
          <p className="mt-1 break-all font-mono text-xs text-muted">
            {riotId.trim()}
          </p>
        ) : null}

        {hasMeta ? (
          <div className="mt-4 space-y-3 border-t border-line-soft/60 pt-3">
            {primaryRole || secondaryRole ? (
              <p className="font-display text-xs uppercase tracking-widest text-muted">
                {primaryRole ?? "—"}
                <span className="mx-2 text-dim">/</span>
                {secondaryRole ?? "—"}
              </p>
            ) : null}

            {agentPool.length > 0 ? (
              <ul className="flex flex-wrap gap-1">
                {agentPool.map((name) => {
                  const pooled = agents.find((a) => a.name === name);
                  return (
                    <li key={name} title={name}>
                      {pooled?.iconUrl ? (
                        <Image
                          src={pooled.iconUrl}
                          alt={name}
                          width={20}
                          height={20}
                          unoptimized
                          className="size-5 border border-line bg-ink/60"
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
            ) : null}

            {bio ? (
              <p className="text-sm leading-relaxed text-muted">{bio}</p>
            ) : null}
          </div>
        ) : null}
      </div>
    </article>
  );
}

