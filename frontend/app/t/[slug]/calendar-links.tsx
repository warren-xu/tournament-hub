import { googleCalendarUrl, type CalendarEvent } from "@/lib/calendar";

const linkClass =
  "font-display text-xs font-semibold uppercase tracking-wider text-muted underline decoration-line underline-offset-4 transition-colors hover:text-bone hover:decoration-accent";

/** Google gets a template link; everything else imports the .ics file. */
export function CalendarLinks({ event, slug }: { event: CalendarEvent; slug: string }) {
  return (
    <p className="flex flex-wrap items-center gap-x-4 gap-y-2">
      <span className="eyebrow">Add to calendar</span>
      <a href={googleCalendarUrl(event)} target="_blank" rel="noreferrer" className={linkClass}>
        Google
      </a>
      <a href={`/t/${slug}/calendar.ics`} download className={linkClass}>
        Apple / Outlook (.ics)
      </a>
    </p>
  );
}
