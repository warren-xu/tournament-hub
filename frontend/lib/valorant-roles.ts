/** Riot's own wording and icon for an agent role, as valorant-api.com publishes it. */
export interface RoleInfo {
  description: string;
  iconUrl: string | null;
}

interface ApiAgent {
  role?: { displayName?: string; description?: string; displayIcon?: string } | null;
}

/**
 * Role name ("Duelist") to Riot's description of it. Read straight from valorant-api.com
 * rather than synced into our database: four short strings that change about never.
 * Cached for a day; if the API is down the result is empty and roles simply show no popup.
 */
export async function getRoleInfo(): Promise<Record<string, RoleInfo>> {
  try {
    const res = await fetch("https://valorant-api.com/v1/agents?isPlayableCharacter=true", {
      next: { revalidate: 86400 },
    });
    if (!res.ok) return {};
    const { data } = (await res.json()) as { data?: ApiAgent[] };
    const roles: Record<string, RoleInfo> = {};
    for (const agent of data ?? []) {
      const role = agent.role;
      if (role?.displayName && role.description && !roles[role.displayName]) {
        roles[role.displayName] = { description: role.description, iconUrl: role.displayIcon ?? null };
      }
    }
    return roles;
  } catch {
    return {};
  }
}
