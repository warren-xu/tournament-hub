/**
 * Add-to-calendar links for a tournament: a Google Calendar template link and an .ics file
 * for everything else (Apple Calendar, Outlook). No accounts involved; the event is only
 * as up to date as the moment it was added.
 */

/** Tournaments have no end time; this is long enough to cover a draft and a few games. */
export const EVENT_HOURS = 3;

export interface CalendarEvent {
  /** Stable across downloads, so re-importing updates the event instead of duplicating it. */
  uid: string;
  title: string;
  startsAt: string;
  /** The tournament page, as an absolute URL. */
  url: string;
}

/** 2026-09-29T18:00:00.000Z → 20260929T180000Z, the UTC form both formats accept. */
function stamp(date: Date): string {
  return date.toISOString().replace(/[-:]/g, "").replace(/\.\d{3}/, "");
}

function eventWindow(event: CalendarEvent) {
  const start = new Date(event.startsAt);
  return { start, end: new Date(start.getTime() + EVENT_HOURS * 3_600_000) };
}

export function googleCalendarUrl(event: CalendarEvent): string {
  const { start, end } = eventWindow(event);
  const params = new URLSearchParams({
    action: "TEMPLATE",
    text: event.title,
    dates: `${stamp(start)}/${stamp(end)}`,
    details: `Draft, teams and rosters: ${event.url}`,
    location: event.url,
  });
  return `https://calendar.google.com/calendar/render?${params}`;
}

/** RFC 5545 text: backslash-escape the separators and flatten newlines. */
function text(value: string): string {
  return value.replace(/\\/g, "\\\\").replace(/;/g, "\\;").replace(/,/g, "\\,").replace(/\r?\n/g, "\\n");
}

/** Lines longer than 75 characters continue on the next line after a leading space. */
function fold(line: string): string {
  const parts: string[] = [];
  for (let i = 0; i < line.length; i += i === 0 ? 75 : 74) {
    parts.push(line.slice(i, i + (i === 0 ? 75 : 74)));
  }
  return parts.join("\r\n ");
}

export function icsFile(event: CalendarEvent, now: Date = new Date()): string {
  const { start, end } = eventWindow(event);
  return [
    "BEGIN:VCALENDAR",
    "VERSION:2.0",
    "PRODID:-//Warrenament//Tournaments//EN",
    "CALSCALE:GREGORIAN",
    "METHOD:PUBLISH",
    "BEGIN:VEVENT",
    `UID:${event.uid}`,
    `DTSTAMP:${stamp(now)}`,
    `DTSTART:${stamp(start)}`,
    `DTEND:${stamp(end)}`,
    `SUMMARY:${text(event.title)}`,
    `DESCRIPTION:${text(`Draft, teams and rosters: ${event.url}`)}`,
    `URL:${event.url}`,
    "END:VEVENT",
    "END:VCALENDAR",
  ].map(fold).join("\r\n") + "\r\n";
}

/** The calendar event for a tournament, or null while its date is still to be announced. */
export function tournamentEvent(
  tournament: { id: number; name: string; slug: string; startsAt: string | null },
  origin: string,
): CalendarEvent | null {
  if (!tournament.startsAt) return null;
  return {
    uid: `tournament-${tournament.id}@warrenament`,
    title: tournament.name,
    startsAt: tournament.startsAt,
    url: `${origin}/t/${tournament.slug}`,
  };
}
