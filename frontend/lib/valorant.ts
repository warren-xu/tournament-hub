/**
 * Reference data lives in the database and is synced from valorant-api.com at
 * /admin/game-data. Agents and competitive ranks both used to be hardcoded here and
 * went stale with every patch.
 */

/** Fallback role list, used only when the agent table is empty or unreachable. */
export const ROLES = ["Duelist", "Initiator", "Controller", "Sentinel"];

/** Groups items by a key, preserving the order the backend sorted them into. */
export function groupBy<T>(
  items: T[],
  key: (item: T) => string,
): Array<[string, T[]]> {
  const groups = new Map<string, T[]>();
  for (const item of items) {
    const k = key(item);
    const bucket = groups.get(k);
    if (bucket) bucket.push(item);
    else groups.set(k, [item]);
  }
  return [...groups.entries()];
}

export const groupByRole = <T extends { role: string }>(items: T[]) =>
  groupBy(items, (item) => item.role);

export const groupByDivision = <T extends { division: string }>(items: T[]) =>
  groupBy(items, (item) => item.division);

/** Riot ships colours as RGBA hex without a leading hash: "868986ff". */
export function rankColor(color: string | null): string | undefined {
  if (!color) return undefined;
  return `#${color.length === 8 ? color.slice(0, 6) : color}`;
}
