import { cookies } from "next/headers";
import type {
  AgentView,
  AuctionSnapshot,
  Me,
  ProfileView,
  PlayerCardView,
  RankView,
  RegistrationView,
  TeamListView,
  TournamentView,
} from "./types";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8080";

/**
 * Server Components talk to Spring directly rather than back through our own
 * rewrite, forwarding the caller's session cookie so the backend sees the real user.
 */
async function get<T>(path: string): Promise<T | null> {
  const cookieHeader = (await cookies()).toString();

  let res: Response;
  try {
    res = await fetch(BACKEND + path, {
      headers: cookieHeader ? { cookie: cookieHeader } : {},
      cache: "no-store",
    });
  } catch {
    // Backend down: render the page's empty state instead of a crash overlay.
    return null;
  }

  if (res.status === 204 || res.status === 401 || res.status === 403) return null;
  if (!res.ok) return null;

  const text = await res.text();
  return text ? (JSON.parse(text) as T) : null;
}

export const getMe = () => get<Me>("/api/me");
export const getAgents = (includeRetired = false) =>
  get<AgentView[]>(`/api/agents?includeRetired=${includeRetired}`);
export const getRanks = (includeHidden = false) =>
  get<RankView[]>(`/api/ranks?includeHidden=${includeHidden}`);
export const getPlayerCards = () => get<PlayerCardView[]>("/api/player-cards");
export const getProfiles = () => get<ProfileView[]>("/api/profiles");
export const getMyProfile = () => get<ProfileView>("/api/profiles/me");
export const getTournaments = () => get<TournamentView[]>("/api/tournaments");
export const getTournamentBySlug = (slug: string) =>
  get<TournamentView>(`/api/tournaments/slug/${encodeURIComponent(slug)}`);
export const getRegistrations = (tournamentId: number) =>
  get<RegistrationView[]>(`/api/tournaments/${tournamentId}/registrations`);
export const getTeams = (tournamentId: number) =>
  get<TeamListView[]>(`/api/tournaments/${tournamentId}/teams`);
export const getAuctionByTournament = (tournamentId: number) =>
  get<AuctionSnapshot>(`/api/auctions/by-tournament/${tournamentId}`);

/** True when the backend is unreachable, so pages can say so plainly. */
export async function backendReachable(): Promise<boolean> {
  try {
    const res = await fetch(BACKEND + "/actuator/health", { cache: "no-store" });
    return res.ok;
  } catch {
    return false;
  }
}
