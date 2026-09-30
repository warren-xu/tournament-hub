"use client";

import { useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { buttonClass } from "@/components/ui";
import { LocalTime } from "@/components/local-time";
import { api, ApiCallError } from "@/lib/client-api";
import type { ProfileView, TournamentView } from "@/lib/types";

const inputClass = "mt-2 w-full rounded-lg border border-line bg-ink px-3 py-3 text-bone";
const steps = ["Event details", "Team captains", "Review & create"];

export function TournamentCreator({ profiles }: { profiles: ProfileView[] }) {
  const router = useRouter();
  const heading = useRef<HTMLHeadingElement>(null);
  const submitting = useRef(false);
  const [step, setStep] = useState(0);
  const [name, setName] = useState("");
  const [credits, setCredits] = useState("100");
  // datetime-local value in the admin's own time zone; empty means "to be announced".
  const [startsAt, setStartsAt] = useState("");
  const [captains, setCaptains] = useState<string[]>(["", ""]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const captainIds = captains.filter(Boolean).map(Number);
  const startsAtIso = startsAt ? new Date(startsAt).toISOString() : null;
  const detailsValid = name.trim().length > 0 && name.trim().length <= 128 && Number.isInteger(Number(credits)) && Number(credits) >= 1 && Number(credits) <= 100000;
  const captainsValid = captainIds.length === captains.length && new Set(captainIds).size === captains.length;
  const valid = detailsValid && captainsValid;

  function go(next: number) {
    setStep(next); setError(null);
    requestAnimationFrame(() => heading.current?.focus());
  }
  async function create() {
    if (!valid || submitting.current) return;
    submitting.current = true; setBusy(true); setError(null);
    try {
      const tournament = await api<TournamentView>("/api/tournaments", { method: "POST", json: {
        name: name.trim(), creditBudget: Number(credits), minBid: 1, startsAt: startsAtIso, captainProfileIds: captainIds,
      } });
      router.push(`/t/${tournament.slug}`); router.refresh();
    } catch (err) {
      setError(err instanceof ApiCallError ? err.message : "We couldn’t create the tournament. Check your connection and try again.");
      submitting.current = false; setBusy(false);
    }
  }

  return <div className="grid items-start gap-6 lg:grid-cols-[1fr_260px]">
    <div className="min-w-0 rounded-2xl border border-line bg-panel p-5 sm:p-8" aria-busy={busy}>
      <ol aria-label="Tournament setup progress" className="mb-8 grid grid-cols-3 gap-2">
        {steps.map((title, i) => <li key={title} aria-current={step === i ? "step" : undefined} className={`border-t-2 pt-3 text-xs ${i === step ? "border-accent text-bone" : "border-line text-muted"}`}><span className="mb-1 block font-mono">0{i + 1}</span>{title}</li>)}
      </ol>
      <h2 ref={heading} tabIndex={-1} className="text-2xl">{steps[step]}</h2>
      <fieldset disabled={busy} className="mt-5 min-w-0 space-y-5">
        {step === 0 ? <>
          <label className="block text-sm">Tournament name<input autoComplete="off" maxLength={128} value={name} onChange={e => setName(e.target.value)} placeholder="e.g. Friday Night Cup" className={inputClass} /></label>
          <label className="block text-sm">Starting credits per team<input type="number" min={1} max={100000} step={1} value={credits} onChange={e => setCredits(e.target.value)} className={inputClass} aria-describedby="credits-help" /></label>
          {credits !== "" && (!Number.isInteger(Number(credits)) || Number(credits) < 1 || Number(credits) > 100000) ? <p role="alert" className="text-sm text-signal">Enter a whole number from 1 to 100,000 credits.</p> : null}
          <p id="credits-help" className="text-sm text-muted">Each captain gets this many credits to bid on players. 100 is a good starting point. Bids start at 1 credit, and a captain who doesn’t want a player just doesn’t bid.</p>
          <label className="block text-sm">Start date and time <span className="text-muted">(optional)</span><input type="datetime-local" value={startsAt} onChange={e => setStartsAt(e.target.value)} className={inputClass} aria-describedby="starts-help" /></label>
          <p id="starts-help" className="text-sm text-muted">In your time zone. Shown on the tournament page with add-to-calendar links. Leave it blank if the date isn’t set yet; you can add it later.</p>
        </> : null}
        {step === 1 ? <>
          <p className="text-sm text-muted">Each team needs a different captain. Captains automatically join their own team and aren’t put up for auction.</p>
          <label className="block text-sm">Number of teams<select value={captains.length} className={inputClass} onChange={e => setCaptains(Array.from({ length: Number(e.target.value) }, (_, i) => captains[i] ?? ""))}>{Array.from({ length: profiles.length - 1 }, (_, i) => i + 2).map(n => <option key={n} value={n}>{n} teams</option>)}</select></label>
          {captains.map((id, i) => <label key={i} className="block text-sm">Team {i + 1} captain<select value={id} onChange={e => setCaptains(captains.map((c, j) => j === i ? e.target.value : c))} className={inputClass}><option value="">Choose a captain…</option>{profiles.map(p => <option key={p.id} value={p.id} disabled={captainIds.includes(p.id) && Number(id) !== p.id}>{p.username}{p.riotId ? ` · ${p.riotId}` : ""}</option>)}</select></label>)}
          <p className="text-sm text-muted" role="status">{captainIds.length} of {captains.length} captains selected</p>
        </> : null}
        {step === 2 ? <>
          <h3 className="text-xl">{name.trim()}</h3>
          <p className="text-sm text-muted">Starts {startsAtIso ? <LocalTime iso={startsAtIso} /> : "on a date to be announced"}</p>
          <ul className="space-y-2">{captainIds.map((id, i) => { const p = profiles.find(p => p.id === id)!; return <li key={id} className="rounded-lg border border-line p-3"><strong>{p.username}</strong><p className="mt-1 text-sm text-muted">Team {i + 1} · named after its captain, renameable later</p></li>; })}</ul>
          <p className="text-sm text-muted">Sign-ups open as soon as you create the tournament: players who sign up join the draft pool and go straight into the auction queue. From the tournament page you can also add any other player by hand until the draft starts. Team size is set when you start it.</p>
        </> : null}
      </fieldset>
      {error ? <p role="alert" className="mt-5 rounded-lg border border-accent p-4 text-sm text-signal">{error}</p> : null}
      <div className="mt-8 flex justify-between gap-3 border-t border-line pt-5">
        <button type="button" disabled={step === 0 || busy} onClick={() => go(step - 1)} className={buttonClass("ghost")}>← Back</button>
        {step < 2 ? <button type="button" disabled={![detailsValid, captainsValid][step]} onClick={() => go(step + 1)} className={buttonClass("primary")}>Continue →</button> : <button type="button" disabled={!valid || busy} onClick={create} className={buttonClass("primary")}>{busy ? "Creating tournament…" : "Create tournament"}</button>}
      </div>
    </div>
    <aside className="rounded-2xl border border-line bg-panel p-6 lg:sticky lg:top-24">
      <h2 className="text-lg">Your event at a glance</h2>
      <dl className="mt-5 space-y-4 text-sm">{[["Teams", captains.length], ["Captains chosen", `${captainIds.length} / ${captains.length}`], ["Queue", "Sign-ups + your picks"], ["People per team", "Set at draft start"]].map(([label, value]) => <div key={label} className="flex justify-between gap-3"><dt className="text-muted">{label}</dt><dd className="text-right font-semibold">{value}</dd></div>)}</dl>
      <p className="mt-6 border-t border-line pt-4 text-xs leading-relaxed text-muted">For example: 4 captains + 16 queued players makes 4 teams of 5. Each team includes its captain.</p>
    </aside>
  </div>;
}
