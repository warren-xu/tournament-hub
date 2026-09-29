"use client";

import { useSyncExternalStore } from "react";

const noop = () => () => {};

/**
 * A timestamp in the viewer's own time zone and locale. The server knows neither, so the
 * server render and the first client render both show plain UTC, built by hand rather than
 * with Intl (the server's and browser's locale data differ, and the two renders must match
 * exactly to hydrate). The browser then swaps in local time.
 */
export function LocalTime({ iso, className }: { iso: string; className?: string }) {
  const inBrowser = useSyncExternalStore(noop, () => true, () => false);
  const date = new Date(iso);
  const text = inBrowser
    ? new Intl.DateTimeFormat(undefined, {
        weekday: "short",
        month: "short",
        day: "numeric",
        hour: "numeric",
        minute: "2-digit",
        timeZoneName: "short",
      }).format(date)
    : `${date.toISOString().slice(0, 16).replace("T", " ")} UTC`;
  return <time dateTime={iso} className={className}>{text}</time>;
}
