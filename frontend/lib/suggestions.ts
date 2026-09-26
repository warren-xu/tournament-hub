"use client";

import { useSyncExternalStore } from "react";

/**
 * Whether to show hints such as the pool's "Drag me!" bubble. A per-browser preference,
 * so it lives in localStorage; every read and write tolerates storage being unavailable
 * (private windows, blocked site data), in which case hints simply stay on.
 */
const KEY = "warrenament:suggestions";
const CHANGED = "warrenament:suggestions-changed";

function read(): boolean {
  try {
    return window.localStorage.getItem(KEY) !== "off";
  } catch {
    return true;
  }
}

function subscribe(onChange: () => void) {
  // "storage" covers other tabs; the custom event covers this one.
  window.addEventListener("storage", onChange);
  window.addEventListener(CHANGED, onChange);
  return () => {
    window.removeEventListener("storage", onChange);
    window.removeEventListener(CHANGED, onChange);
  };
}

export function setSuggestionsEnabled(enabled: boolean) {
  try {
    if (enabled) window.localStorage.removeItem(KEY);
    else window.localStorage.setItem(KEY, "off");
  } catch {
    // Not persisted; nothing else to do.
  }
  window.dispatchEvent(new Event(CHANGED));
}

/** `null` during server rendering and hydration, so a hint never flashes for someone who turned them off. */
export function useSuggestionsEnabled(): boolean | null {
  return useSyncExternalStore(subscribe, read, () => null);
}
