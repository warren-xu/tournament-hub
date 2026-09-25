"use client";

import Image from "next/image";
import { PlayerCardPicker } from "@/components/player-card-picker";
import { PlayerCard } from "@/components/player-card";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { buttonClass } from "@/components/ui";
import { api, ApiCallError } from "@/lib/client-api";
import type { AgentView, ProfileView, RankView, PlayerCardView } from "@/lib/types";
import {
  groupByDivision,
  groupByRole,
  rankColor,
  ROLES,
} from "@/lib/valorant";

type Status = { kind: "idle" | "saving" | "saved" | "error"; message?: string };

export function ProfileForm({
  username,
  initial,
  agents,
  ranks,
  playerCards,
}: {
  username: string;
  initial: ProfileView | null;
  agents: AgentView[];
  ranks: RankView[];
  playerCards: PlayerCardView[];
}) {
  const router = useRouter();
  const [riotId, setRiotId] = useState(initial?.riotId ?? "");
  const [currentRank, setCurrentRank] = useState(initial?.currentRank ?? "");
  const [primaryRole, setPrimaryRole] = useState(initial?.primaryRole ?? "");
  const [secondaryRole, setSecondaryRole] = useState(initial?.secondaryRole ?? "");
  const [bio, setBio] = useState(initial?.bio ?? "");
  const [selected, setSelected] = useState<string[]>(initial?.agents ?? []);
  const [mainAgent, setMainAgent] = useState(initial?.mainAgent ?? "");
  const [playerCard, setPlayerCard] = useState<PlayerCardView | null>(initial?.playerCard ?? null);
  const [status, setStatus] = useState<Status>({ kind: "idle" });

  const byRole = groupByRole(agents);
  const byDivision = groupByDivision(ranks);
  // Role options follow the agent table, so a new role category needs no code change.
  const roleOptions = byRole.length > 0 ? byRole.map(([role]) => role) : ROLES;

  function toggleAgent(agent: string) {
    setStatus({ kind: "idle" });
    if (mainAgent === agent) setMainAgent("");
    setSelected((current) =>
      current.includes(agent)
        ? current.filter((a) => a !== agent)
        : [...current, agent],
    );
  }

  async function save(event: React.FormEvent) {
    event.preventDefault();
    setStatus({ kind: "saving" });
    try {
      await api<ProfileView>("/api/profiles/me", {
        method: "PUT",
        json: {
          riotId: riotId.trim() || null,
          currentRank: currentRank || null,
          primaryRole: primaryRole || null,
          secondaryRole: secondaryRole || null,
          bio: bio.trim() || null,
          agents: selected,
          mainAgent: mainAgent || null,
          playerCardId: playerCard?.id ?? null,
        },
      });
      setStatus({ kind: "saved" });
      router.refresh();
    } catch (err) {
      setStatus({
        kind: "error",
        message:
          err instanceof ApiCallError ? err.message : "Could not save your card.",
      });
    }
  }

  return (
    <form onSubmit={save} onChange={() => setStatus({ kind: "idle" })} className="mt-8 grid items-start gap-8 lg:grid-cols-[minmax(0,640px)_minmax(0,1fr)] lg:gap-12">
      <aside className="lg:sticky lg:top-8 lg:col-start-2 lg:row-start-1">
        <p className="eyebrow mb-3">Live card preview</p>
        {/* Capped at the art's native 268px width: any wider and the card art upscales,
            so the preview would look softer than the real thing on the players page. */}
        <div className="w-full max-w-67">
          <PlayerCard username={username} riotId={riotId} mainAgent={mainAgent}
            currentRank={currentRank} agents={agents} ranks={ranks} playerCard={playerCard} />
          <p className="mt-3 text-xs text-muted">Choose your artwork, main agent and rank to make it yours. Save your card to share it with captains.</p>
        </div>
      </aside>
      <div className="space-y-8 lg:col-start-1 lg:row-start-1">
        <Field label="Riot ID" hint="Name and tagline, e.g. boombot#asked">
          <input
            value={riotId}
            onChange={(e) => setRiotId(e.target.value)}
            placeholder="boombot#asked"
            maxLength={64}
            className={inputClass}
          />
        </Field>

        <div className="grid gap-5 sm:grid-cols-2">
          <Field label="Current rank">
            <RankSelect
              value={currentRank}
              onChange={setCurrentRank}
              ranks={ranks}
              groups={byDivision}
            />
          </Field>
          <Field label="Primary role">
            <Select value={primaryRole} onChange={setPrimaryRole} options={roleOptions} />
          </Field>
          <Field label="Secondary role">
            <Select
              value={secondaryRole}
              onChange={setSecondaryRole}
              options={roleOptions}
            />
          </Field>
        </div>

        <fieldset>
          <legend className="eyebrow">
            Agent pool
            {selected.length > 0 ? (
              <span className="tabular ml-2 text-muted">{selected.length} selected</span>
            ) : null}
          </legend>
          {byRole.length === 0 ? (
            <p className="mt-3 text-sm text-dim">
              No agents have been set up yet. An admin adds them at{" "}
              <code className="bg-raise px-1 py-0.5 font-mono text-xs">/admin/agents</code>.
            </p>
          ) : (
            <div className="mt-3 space-y-4">
              {byRole.map(([role, roleAgents]) => (
                <div key={role}>
                  <p className="mb-2 font-display text-xs uppercase tracking-widest text-muted">
                    {role}
                  </p>
                  <div className="flex flex-wrap gap-1.5">
                    {roleAgents.map((agent) => {
                      const on = selected.includes(agent.name);
                      return (
                        <button
                          key={agent.id}
                          type="button"
                          onClick={() => toggleAgent(agent.name)}
                          aria-pressed={on}
                          // The portrait carries no text, so the name has to reach
                          // assistive tech and hover some other way.
                          aria-label={agent.name}
                          title={agent.name}
                          className={`relative block border transition-colors ${
                            on
                              ? "border-accent bg-accent/15"
                              : "border-line bg-panel hover:border-dim"
                          }`}
                        >
                          {agent.iconUrl ? (
                            <Image
                              src={agent.iconUrl}
                              alt=""
                              width={48}
                              height={48}
                              unoptimized
                              className={`size-12 transition-[filter,opacity] ${
                                on ? "" : "opacity-45 grayscale hover:opacity-80"
                              }`}
                            />
                          ) : (
                            // Unsynced agent: fall back to the name rather than a blank box.
                            <span
                              className={`grid size-12 place-items-center px-1 text-center font-display text-[0.625rem] font-semibold uppercase leading-tight ${
                                on ? "text-bone" : "text-muted"
                              }`}
                            >
                              {agent.name}
                            </span>
                          )}
                          {on ? (
                            <span
                              aria-hidden
                              className="absolute inset-x-0 bottom-0 block h-0.5 bg-accent"
                            />
                          ) : null}
                        </button>
                      );
                    })}
                  </div>
                </div>
              ))}
            </div>
          )}
        </fieldset>

        <Field label="Main agent" hint="Featured on your player card">
          <Select value={mainAgent} onChange={setMainAgent} options={selected} />
          <span className="mt-2 block text-xs text-muted">Pick from your agent pool above.</span>
        </Field>

        <Field label="Notes for captains" hint={`${bio.length}/1000`}>
          <textarea
            value={bio}
            onChange={(e) => setBio(e.target.value.slice(0, 1000))}
            rows={4}
            placeholder="Availability, comms, what you actually want to play."
            className={`${inputClass} resize-y`}
          />
        </Field>

        <PlayerCardPicker cards={playerCards} selected={playerCard} onChange={(card) => {
          setPlayerCard(card);
          setStatus({ kind: "idle" });
        }} />

        <div className="flex items-center gap-4 border-t border-line-soft pt-6">
          <button
            type="submit"
            disabled={status.kind === "saving"}
            className={buttonClass("primary")}
          >
            {status.kind === "saving" ? "Saving…" : "Save card"}
          </button>

          <p aria-live="polite" className="text-sm">
            {status.kind === "saved" ? (
              <span className="text-muted">Saved.</span>
            ) : null}
            {status.kind === "error" ? (
              <span className="text-signal">{status.message}</span>
            ) : null}
          </p>
        </div>
      </div>
    </form>
  );
}

const inputClass =
  "w-full border border-line bg-ink px-3 py-2.5 text-sm text-bone placeholder:text-dim focus:border-accent focus:outline-none";

function Field({
  label,
  hint,
  children,
}: {
  label: string;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <label className="block">
      <span className="flex items-baseline justify-between">
        <span className="eyebrow">{label}</span>
        {hint ? <span className="tabular text-xs text-dim">{hint}</span> : null}
      </span>
      <span className="mt-2 block">{children}</span>
    </label>
  );
}

function Select({
  value,
  onChange,
  options,
}: {
  value: string;
  onChange: (value: string) => void;
  options: string[];
}) {
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className={inputClass}
    >
      <option value="">—</option>
      {options.map((option) => (
        <option key={option} value={option}>
          {option}
        </option>
      ))}
    </select>
  );
}

/**
 * A native select keeps this accessible and keyboard-friendly; the tier icon and Riot's
 * own tier colour sit beside it, since option elements cannot carry images.
 */
function RankSelect({
  value,
  onChange,
  ranks,
  groups,
}: {
  value: string;
  onChange: (value: string) => void;
  ranks: RankView[];
  groups: Array<[string, RankView[]]>;
}) {
  const selected = ranks.find((r) => r.name === value);

  if (ranks.length === 0) {
    return (
      <p className="text-sm text-dim">
        No ranks loaded. An admin syncs them at{" "}
        <code className="bg-raise px-1 py-0.5 font-mono text-xs">/admin/game-data</code>.
      </p>
    );
  }

  return (
    <span className="flex items-center gap-2">
      <span
        aria-hidden
        className="grid size-10 shrink-0 place-items-center border border-line bg-ink"
        style={selected?.color ? { borderColor: rankColor(selected.color) } : undefined}
      >
        {selected?.iconUrl ? (
          <Image
            src={selected.iconUrl}
            alt=""
            width={28}
            height={28}
            unoptimized
            className="size-7"
          />
        ) : null}
      </span>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className={inputClass}
      >
        <option value="">—</option>
        {groups.map(([division, tiers]) => (
          <optgroup key={division} label={division}>
            {tiers.map((tier) => (
              <option key={tier.id} value={tier.name}>
                {tier.name}
              </option>
            ))}
          </optgroup>
        ))}
      </select>
    </span>
  );
}
