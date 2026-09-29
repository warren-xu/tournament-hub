"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api, ApiCallError } from "@/lib/client-api";

/** A team's name, which its captain or an admin can rename in place. */
export function TeamName({
  teamId,
  name,
  logoUrl,
  editable,
}: {
  teamId: number;
  name: string;
  logoUrl: string | null;
  editable: boolean;
}) {
  const router = useRouter();
  const [draft, setDraft] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const heading = <h3 className="min-w-0 break-words font-display text-xl uppercase tracking-wide">{name}</h3>;
  if (!editable) return heading;

  if (draft === null) {
    return (
      <div className="flex min-w-0 items-baseline gap-2">
        {heading}
        <button type="button" onClick={() => { setDraft(name); setError(null); }} aria-label={`Rename ${name}`}
          className="shrink-0 text-xs text-muted underline underline-offset-4 hover:text-bone">
          Rename
        </button>
      </div>
    );
  }

  const trimmed = draft.trim();
  async function save() {
    if (!trimmed || trimmed === name) { setDraft(null); return; }
    setBusy(true);
    setError(null);
    try {
      await api(`/api/teams/${teamId}`, { method: "PUT", json: { name: trimmed, logoUrl } });
      setDraft(null);
      router.refresh();
    } catch (err) {
      setError(err instanceof ApiCallError ? err.message : "Couldn't rename the team.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="min-w-0 flex-1 space-y-2" onSubmit={(e) => { e.preventDefault(); void save(); }}>
      <input autoFocus value={draft} maxLength={128} aria-label="Team name" disabled={busy}
        onChange={(e) => setDraft(e.target.value)}
        onKeyDown={(e) => { if (e.key === "Escape") setDraft(null); }}
        className="w-full border border-line bg-ink px-3 py-2 font-display text-lg uppercase tracking-wide text-bone focus:border-accent focus:outline-none" />
      <div className="flex gap-3 text-xs">
        <button type="submit" disabled={busy || !trimmed} className="text-bone underline underline-offset-4 hover:text-signal disabled:opacity-40">
          {busy ? "Saving…" : "Save"}
        </button>
        <button type="button" disabled={busy} onClick={() => setDraft(null)} className="text-muted underline underline-offset-4 hover:text-bone">
          Cancel
        </button>
      </div>
      {error ? <p role="alert" className="text-sm text-signal">{error}</p> : null}
    </form>
  );
}
