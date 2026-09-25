"use client";

import { useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Avatar, buttonClass } from "@/components/ui";
import { api, ApiCallError } from "@/lib/client-api";
import type { ProfileView, TournamentView } from "@/lib/types";

const inputClass = "mt-2 w-full rounded-lg border border-line bg-ink px-3 py-3 text-bone";
const steps = ["Event details", "Team captains", "Draft players", "Review & create"];

export function TournamentCreator({ profiles }: { profiles: ProfileView[] }) {
  const router = useRouter();
  const heading = useRef<HTMLHeadingElement>(null);
  const submitting = useRef(false);
  const [step, setStep] = useState(0);
  const [name, setName] = useState("");
  const [credits, setCredits] = useState("100");
  const [captains, setCaptains] = useState<string[]>(["", ""]);
  const [players, setPlayers] = useState<number[]>([]);
  const [query, setQuery] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const captainIds = captains.filter(Boolean).map(Number);
  const available = profiles.filter(p => !captainIds.includes(p.id));
  const chosen = players.filter(id => !captainIds.includes(id));
  const filtered = available.filter(p => `${p.username} ${p.riotId ?? ""} ${p.currentRank ?? ""}`.toLowerCase().includes(query.toLowerCase()));
  const remaining = chosen.length % captains.length;
  const perTeam = chosen.length / captains.length;
  const detailsValid = name.trim().length > 0 && name.trim().length <= 128 && Number.isInteger(Number(credits)) && Number(credits) >= 1 && Number(credits) <= 100000;
  const captainsValid = captainIds.length === captains.length && new Set(captainIds).size === captains.length;
  const playersValid = chosen.length > 0 && remaining === 0 && perTeam <= 9;
  const valid = detailsValid && captainsValid && playersValid;
  const balance = chosen.length === 0 ? `Choose at least ${captains.length} draft players — one for each captain.`
    : remaining ? `Add ${captains.length - remaining} more or remove ${remaining} to give every team the same number of players.`
    : perTeam > 9 ? `Remove ${chosen.length - captains.length * 9} players. Teams can have up to 10 people, including the captain.`
    : `${chosen.length} draft players ÷ ${captains.length} captains = ${perTeam} players per team, plus their captain.`;

  function go(next: number) {
    setStep(next); setError(null);
    requestAnimationFrame(() => heading.current?.focus());
  }
  function chooseCaptain(index: number, value: string) {
    setCaptains(captains.map((id, i) => i === index ? value : id));
    if (value) setPlayers(players.filter(id => id !== Number(value)));
  }
  async function create() {
    if (!valid || submitting.current) return;
    submitting.current = true; setBusy(true); setError(null);
    try {
      const tournament = await api<TournamentView>("/api/tournaments", { method: "POST", json: {
        name: name.trim(), creditBudget: Number(credits), minBid: 1,
        captainProfileIds: captainIds, playerProfileIds: chosen,
      } });
      router.push(`/t/${tournament.slug}`); router.refresh();
    } catch (err) {
      setError(err instanceof ApiCallError ? err.message : "We couldn’t create the tournament. Check your connection and try again.");
      submitting.current = false; setBusy(false);
    }
  }

  return <div className="grid items-start gap-6 lg:grid-cols-[1fr_260px]">
    <div className="min-w-0 rounded-2xl border border-line bg-panel p-5 sm:p-8" aria-busy={busy}>
      <ol aria-label="Tournament setup progress" className="mb-8 grid grid-cols-4 gap-2">
        {steps.map((title, i) => <li key={title} aria-current={step === i ? "step" : undefined} className={`border-t-2 pt-3 text-xs ${i === step ? "border-accent text-bone" : "border-line text-muted"}`}><span className="mb-1 block font-mono">0{i + 1}</span>{title}</li>)}
      </ol>
      <h2 ref={heading} tabIndex={-1} className="text-2xl">{steps[step]}</h2>
      <fieldset disabled={busy} className="mt-5 min-w-0 space-y-5">
        {step === 0 ? <>
          <label className="block text-sm">Tournament name<input autoComplete="off" maxLength={128} value={name} onChange={e => setName(e.target.value)} placeholder="e.g. Friday Night Cup" className={inputClass} /></label>
          <label className="block text-sm">Starting credits per team<input type="number" min={1} max={100000} step={1} value={credits} onChange={e => setCredits(e.target.value)} className={inputClass} aria-describedby="credits-help" /></label>
          {credits !== "" && (!Number.isInteger(Number(credits)) || Number(credits) < 1 || Number(credits) > 100000) ? <p role="alert" className="text-sm text-signal">Enter a whole number from 1 to 100,000 credits.</p> : null}
          <p id="credits-help" className="text-sm text-muted">Each captain gets this many credits to bid on players. 100 is a good starting point. Bids start at 1 credit.</p>
          <p className="text-sm text-muted">We’ll create the tournament link and calculate team sizes for you.</p>
        </> : null}
        {step === 1 ? <>
          <p className="text-sm text-muted">Each team needs a different captain. Captains automatically join their own team and aren’t included in the draft.</p>
          <label className="block text-sm">Number of teams<select value={captains.length} className={inputClass} onChange={e => setCaptains(Array.from({ length: Number(e.target.value) }, (_, i) => captains[i] ?? ""))}>{Array.from({ length: Math.floor(profiles.length / 2) - 1 }, (_, i) => i + 2).map(n => <option key={n} value={n}>{n} teams</option>)}</select></label>
          {captains.map((id, i) => <label key={i} className="block text-sm">Team {i + 1} captain<select value={id} onChange={e => chooseCaptain(i, e.target.value)} className={inputClass}><option value="">Choose a captain…</option>{profiles.map(p => <option key={p.id} value={p.id} disabled={captainIds.includes(p.id) && Number(id) !== p.id}>{p.username}{p.riotId ? ` · ${p.riotId}` : ""}</option>)}</select></label>)}
          <p className="text-sm text-muted" role="status">{captainIds.length} of {captains.length} captains selected</p>
        </> : null}
        {step === 2 ? <>
          <p className="text-sm text-muted">Choose who will be drafted. Your {captains.length} captains are already on their teams and have been removed from this list.</p>
          <label className="block text-sm">Find a player<input type="search" value={query} onChange={e => setQuery(e.target.value)} placeholder="Search name, Riot ID or rank" className={inputClass} /></label>
          <div className="flex flex-wrap items-center gap-3"><button type="button" className={buttonClass("default")} onClick={() => setPlayers(available.map(p => p.id))}>Select all {available.length}</button><button type="button" className={buttonClass("ghost")} onClick={() => setPlayers([])}>Clear selection</button><span className="text-sm text-muted">{chosen.length} selected</span></div>
          <ul className="max-h-96 space-y-2 overflow-y-auto p-1">{filtered.map(p => <li key={p.id}><label className={`flex cursor-pointer items-center gap-3 rounded-lg border p-3 ${chosen.includes(p.id) ? "border-accent/60 bg-accent/5" : "border-line bg-ink"}`}><input type="checkbox" checked={chosen.includes(p.id)} onChange={e => setPlayers(e.target.checked ? [...players, p.id] : players.filter(id => id !== p.id))} className="size-4 accent-accent" /><Avatar src={p.avatarUrl} name={p.username} /><span className="min-w-0"><span className="block truncate text-sm">{p.username}</span><span className="block truncate text-xs text-muted">{p.riotId || p.currentRank || "No rank set"}</span></span></label></li>)}</ul>
          {filtered.length === 0 ? <p className="text-sm text-muted">No players match your search.</p> : null}
          <p role="status" className={`rounded-lg border p-4 text-sm ${playersValid ? "border-emerald-500/30 bg-emerald-500/5 text-emerald-300" : "border-amber-500/30 bg-amber-500/5 text-amber-200"}`}>{balance}</p>
        </> : null}
        {step === 3 ? <>
          <p className="text-sm text-muted">Everything is balanced. Review your event before creating it.</p>
          <h3 className="text-xl">{name.trim()}</h3>
          <ul className="space-y-2">{captainIds.map((id, i) => { const p = profiles.find(p => p.id === id)!; return <li key={id} className="rounded-lg border border-line p-3"><strong>Team {i + 1} · {p.username}</strong><p className="mt-1 text-sm text-muted">Captain: {p.username} · {perTeam} draft places</p></li>; })}</ul>
          <details className="rounded-lg border border-line p-4"><summary className="cursor-pointer text-sm">View all {chosen.length} draft players</summary><ul className="mt-3 space-y-1 text-sm text-muted">{profiles.filter(p => chosen.includes(p.id)).map(p => <li key={p.id}>{p.username}</li>)}</ul></details>
          <p className="text-sm text-muted">Creating the event sets up every team and approves your chosen players. The draft stays closed until you start it from the tournament page.</p>
        </> : null}
      </fieldset>
      {error ? <p role="alert" className="mt-5 rounded-lg border border-accent p-4 text-sm text-signal">{error}</p> : null}
      <div className="mt-8 flex justify-between gap-3 border-t border-line pt-5">
        <button type="button" disabled={step === 0 || busy} onClick={() => go(step - 1)} className={buttonClass("ghost")}>← Back</button>
        {step < 3 ? <button type="button" disabled={![detailsValid, captainsValid, playersValid][step]} onClick={() => go(step + 1)} className={buttonClass("primary")}>Continue →</button> : <button type="button" disabled={!valid || busy} onClick={create} className={buttonClass("primary")}>{busy ? "Creating tournament…" : "Create tournament"}</button>}
      </div>
    </div>
    <aside className="rounded-2xl border border-line bg-panel p-6 lg:sticky lg:top-24">
      <h2 className="text-lg">Your event at a glance</h2>
      <dl className="mt-5 space-y-4 text-sm">{[["Teams", captains.length], ["Captains chosen", `${captainIds.length} / ${captains.length}`], ["Draft players", chosen.length], ["People per team", playersValid ? perTeam + 1 : "Not balanced yet"], ["Credits per team", credits || "—"]].map(([label, value]) => <div key={label} className="flex justify-between gap-3"><dt className="text-muted">{label}</dt><dd className="text-right font-semibold">{value}</dd></div>)}</dl>
      <p className="mt-6 border-t border-line pt-4 text-xs leading-relaxed text-muted">For example: 4 captains + 16 draft players makes 4 teams of 5. Each team includes its captain.</p>
    </aside>
  </div>;
}
