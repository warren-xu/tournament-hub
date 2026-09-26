"use client";

import { setSuggestionsEnabled, useSuggestionsEnabled } from "@/lib/suggestions";

/** A switch pinned to the bottom-right corner that silences hint bubbles. */
export function SuggestionsToggle() {
  const enabled = useSuggestionsEnabled();
  if (enabled === null) return null;
  const off = !enabled;

  return (
    <button type="button" role="switch" aria-checked={off} className="suggestions-toggle"
      onClick={() => setSuggestionsEnabled(off)}>
      <span className="suggestions-toggle-track" aria-hidden="true">
        <span className="suggestions-toggle-knob" />
      </span>
      Turn off suggestions
    </button>
  );
}
