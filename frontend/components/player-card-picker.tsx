"use client";

import Image from "next/image";
import { useState } from "react";
import { buttonClass } from "@/components/ui";
import type { PlayerCardView } from "@/lib/types";

const PAGE_SIZE = 12;

export function PlayerCardPicker({ cards, selected, onChange }: {
  cards: PlayerCardView[];
  selected: PlayerCardView | null;
  onChange: (card: PlayerCardView | null) => void;
}) {
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const filtered = cards.filter((card) => card.name.toLowerCase().includes(query.trim().toLowerCase()));
  const pages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const currentPage = Math.min(page, pages - 1);
  const visible = filtered.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE);

  return (
    <fieldset className="min-w-0">
      <legend className="eyebrow">Player card artwork</legend>
      <div className="mt-3 flex items-center justify-between gap-3">
        <p aria-live="polite" className="text-sm text-muted">{selected?.name ?? "Using main-agent artwork"}</p>
        {selected ? <button type="button" onClick={() => onChange(null)} className={buttonClass("ghost", "shrink-0")}>Reset</button> : null}
      </div>
      {cards.length === 0 ? (
        <p className="mt-3 text-sm text-muted">No player cards are available yet. An admin can sync them from Game data.</p>
      ) : (
        <>
          <label className="mt-3 block">
            <span className="sr-only">Search player cards</span>
            <input type="search" value={query} onChange={(event) => { setQuery(event.target.value); setPage(0); }}
              placeholder={`Search ${cards.length} player cards…`}
              className="w-full border border-line bg-ink px-3 py-2.5 text-sm text-bone placeholder:text-dim" />
          </label>
          <ul className="mt-3 grid grid-cols-3 gap-2 sm:grid-cols-4">
            {visible.map((card) => (
              <li key={card.id}>
                <button type="button" aria-pressed={selected?.id === card.id} onClick={() => onChange(card)}
                  className={`h-full w-full overflow-hidden border text-left transition-colors ${selected?.id === card.id ? "border-accent bg-accent/10" : "border-line bg-panel hover:border-muted"}`}>
                  <CardThumbnail key={card.largeArt ?? card.id} card={card} />
                  <span className="block px-2 py-2 font-display text-xs leading-snug text-bone">{card.name}</span>
                </button>
              </li>
            ))}
          </ul>
          {filtered.length === 0 ? <p className="mt-3 text-sm text-muted">No cards match your search.</p> : null}
          <div className="mt-3 flex items-center justify-between gap-2">
            <button type="button" disabled={currentPage === 0} onClick={() => setPage(currentPage - 1)} className={buttonClass("default")}>Previous</button>
            <p aria-live="polite" className="tabular text-center text-xs text-muted">{filtered.length} cards · {currentPage + 1} / {pages}</p>
            <button type="button" disabled={currentPage >= pages - 1} onClick={() => setPage(currentPage + 1)} className={buttonClass("default")}>Next</button>
          </div>
        </>
      )}
    </fieldset>
  );
}

function CardThumbnail({ card }: { card: PlayerCardView }) {
  const [failed, setFailed] = useState(false);
  const src = card.largeArt;
  return (
    <span className="relative block aspect-[268/640] bg-raise">
      {src && !failed ? <Image src={src} alt="" fill quality={90} sizes="(max-width: 640px) 28vw, (max-width: 1024px) 22vw, 180px" className="object-contain" onError={() => setFailed(true)} />
        : <span className="absolute inset-0 grid place-items-center text-center text-xs text-muted">No preview</span>}
    </span>
  );
}
