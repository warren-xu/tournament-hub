import Image from "next/image";
import Link from "next/link";
import type { ReactNode } from "react";
import type { AgentView, RankView } from "@/lib/types";
import { rankColor } from "@/lib/valorant";

/** Hard-edged surface with a 1px border. No shadows, no blur, no radius. */
export function Panel({
  children,
  className = "",
  as: Tag = "div",
}: {
  children: ReactNode;
  className?: string;
  as?: "div" | "section" | "article" | "aside";
}) {
  return (
    <Tag className={`border border-line bg-panel ${className}`}>{children}</Tag>
  );
}

export function Eyebrow({ children }: { children: ReactNode }) {
  return <p className="eyebrow">{children}</p>;
}

/** Left-aligned section head: label, title, and an optional trailing action. */
export function SectionHead({
  label,
  title,
  action,
}: {
  label: string;
  title: string;
  action?: ReactNode;
}) {
  return (
    <div className="mb-4 flex items-end justify-between gap-4 border-b border-line-soft pb-3">
      <div>
        <Eyebrow>{label}</Eyebrow>
        <h2 className="mt-1 text-xl uppercase tracking-wide">{title}</h2>
      </div>
      {action}
    </div>
  );
}

type ButtonTone = "primary" | "default" | "ghost" | "danger";

export function buttonClass(tone: ButtonTone = "default", extra = ""): string {
  const base =
    "inline-flex items-center justify-center gap-2 min-h-12 px-5 py-3 font-display text-sm font-semibold uppercase tracking-wider transition-colors disabled:cursor-not-allowed disabled:opacity-40";
  const tones: Record<ButtonTone, string> = {
    primary:
      "corner-cut-sm bg-accent text-white hover:bg-accent-deep disabled:hover:bg-accent",
    default:
      "border border-line bg-raise text-bone hover:border-dim hover:bg-line-soft",
    ghost: "text-muted hover:text-bone",
    danger: "border border-accent-deep text-bone hover:bg-accent-deep hover:text-white",
  };
  return `${base} ${tones[tone]} ${extra}`;
}

export function LinkButton({
  href,
  tone = "default",
  children,
  className = "",
}: {
  href: string;
  tone?: ButtonTone;
  children: ReactNode;
  className?: string;
}) {
  return (
    <Link href={href} className={buttonClass(tone, className)}>
      {children}
    </Link>
  );
}

export function Tag({
  children,
  tone = "muted",
}: {
  children: ReactNode;
  tone?: "muted" | "accent" | "outline";
}) {
  const tones = {
    muted: "bg-raise text-muted",
    accent: "bg-accent text-white",
    outline: "border border-line text-muted",
  };
  return (
    <span
      className={`px-2 py-0.5 font-display text-[0.6875rem] font-semibold uppercase tracking-widest ${tones[tone]}`}
    >
      {children}
    </span>
  );
}

/** Label above a number, in tabular figures so it never reflows as it changes. */
export function Stat({
  label,
  value,
  accent = false,
  suffix,
}: {
  label: string;
  value: ReactNode;
  accent?: boolean;
  suffix?: string;
}) {
  return (
    <div>
      <p className="eyebrow">{label}</p>
      <p
        className={`tabular mt-1 font-display text-2xl font-semibold ${
          accent ? "text-signal" : "text-bone"
        }`}
      >
        {value}
        {suffix ? <span className="ml-1 text-sm text-muted">{suffix}</span> : null}
      </p>
    </div>
  );
}

export function EmptyState({
  title,
  detail,
  action,
}: {
  title: string;
  detail: string;
  action?: ReactNode;
}) {
  return (
    <div className="border border-dashed border-line px-6 py-12 text-center">
      <p className="font-display text-base uppercase tracking-wide text-muted">
        {title}
      </p>
      <p className="mx-auto mt-2 max-w-md text-sm text-dim">{detail}</p>
      {action ? <div className="mt-5">{action}</div> : null}
    </div>
  );
}

/** Backend-is-down banner. Shown instead of pretending the page has no data. */
export function OfflineNotice() {
  return (
    <div className="border border-accent-deep bg-accent-deep/10 px-4 py-3">
      <p className="font-display text-sm font-semibold uppercase tracking-wider text-signal">
        Temporarily unavailable
      </p>
      <p className="mt-1 text-sm text-muted">
        Tournament data could not be loaded. Please try refreshing in a moment.
      </p>
    </div>
  );
}

export function Avatar({
  src,
  name,
  size = 32,
}: {
  src: string | null;
  name: string;
  size?: number;
}) {
  if (src) {
    return (
      <Image
        src={src}
        alt=""
        width={size}
        height={size}
        unoptimized
        className="shrink-0 border border-line object-cover"
        style={{ width: size, height: size }}
      />
    );
  }
  return (
    <span
      aria-hidden
      className="grid shrink-0 place-items-center border border-line bg-raise font-display text-xs font-semibold text-dim"
      style={{ width: size, height: size }}
    >
      {name.slice(0, 2).toUpperCase()}
    </span>
  );
}

/**
 * Profiles store the rank by name, so the icon is resolved through the synced rank
 * table at render time. Falls back to plain text when the name predates a sync.
 */
export function RankBadge({
  name,
  ranks,
  size = 20,
}: {
  name: string | null;
  ranks: Map<string, RankView>;
  size?: number;
}) {
  if (!name) return null;
  const rank = ranks.get(name.toLowerCase());

  return (
    <span className="inline-flex items-center gap-1.5">
      {rank?.iconUrl ? (
        <Image
          src={rank.iconUrl}
          alt=""
          width={size}
          height={size}
          unoptimized
          style={{ width: size, height: size }}
          className="shrink-0"
        />
      ) : null}
      <span
        className="font-display text-sm uppercase tracking-wide"
        style={rank?.color ? { color: rankColor(rank.color) } : undefined}
      >
        {name}
      </span>
    </span>
  );
}

/** Builds the lookup RankBadge expects. */
export function rankIndex(ranks: RankView[]): Map<string, RankView> {
  return new Map(ranks.map((r) => [r.name.toLowerCase(), r]));
}

/** Agent name with its synced portrait. Falls back to a plain chip when unsynced. */
export function AgentChip({
  name,
  agents,
}: {
  name: string;
  agents: Map<string, AgentView>;
}) {
  const agent = agents.get(name.toLowerCase());

  return (
    <span
      className={`inline-flex items-center gap-1.5 border border-line py-0.5 pr-2 font-display text-[0.6875rem] font-semibold uppercase tracking-widest text-muted ${
        agent?.iconUrl ? "pl-0.5" : "pl-2"
      }`}
    >
      {agent?.iconUrl ? (
        <Image
          src={agent.iconUrl}
          alt=""
          width={20}
          height={20}
          unoptimized
          className="size-5 shrink-0 bg-raise"
        />
      ) : null}
      {name}
    </span>
  );
}

export function agentIndex(agents: AgentView[]): Map<string, AgentView> {
  return new Map(agents.map((a) => [a.name.toLowerCase(), a]));
}

/**
 * Spread onto any control whose disabled state changes. Firefox restores a control's
 * enabled state on reload unless told not to, which leaves the DOM disagreeing with React
 * at hydration. React's types omit autoComplete on buttons, though the attribute is valid
 * there, hence the spread.
 */
export const NO_FORM_RESTORE = { autoComplete: "off" };
