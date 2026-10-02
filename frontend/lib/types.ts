/** Mirrors the DTOs in com.warren.warrenament.*.**Dtos. */

export type Role = "PLAYER" | "ADMIN";
export type TournamentStatus =
  | "DRAFT"
  | "REGISTRATION"
  | "DRAFTING"
  | "LIVE"
  | "COMPLETE";
export type RegistrationStatus = "PENDING" | "APPROVED" | "REJECTED";
export type AuctionStatus = "SETUP" | "LIVE" | "PAUSED" | "COMPLETE";
export type LotStatus = "PENDING" | "OPEN" | "SOLD" | "UNSOLD";

export interface AgentView {
  id: number;
  name: string;
  role: string;
  active: boolean;
  displayOrder: number;
  iconUrl: string | null;
  /** Full-body art, used as the overlay on a player card. */
  portraitUrl: string | null;
  /**
   * Where the agent's body sits across that art, 0..1. Riot does not centre them on
   * the shared canvas, so each portrait is nudged by its own measured amount.
   */
  portraitFocusX: number;
}

export interface PlayerCardView {
  id: number;
  name: string;
  smallArt: string | null;
  largeArt: string | null;
}

export interface RankView {
  id: number;
  /** Riot's numeric tier: the correct ascending sort order. */
  tier: number;
  name: string;
  division: string;
  /** Riot's RGBA hex for the tier, e.g. "868986ff". */
  color: string | null;
  iconUrl: string | null;
  active: boolean;
}

export interface SyncResult {
  added: number;
  updated: number;
  unchanged: number;
  /** Rows held locally the source no longer lists. Reported, never deleted. */
  notInSource: string[];
  note: string | null;
}

export interface Me {
  userId: number;
  username: string;
  avatarUrl: string | null;
  role: Role;
}

export interface ProfileView {
  id: number;
  userId: number;
  username: string;
  avatarUrl: string | null;
  riotId: string | null;
  currentRank: string | null;
  primaryRole: string | null;
  secondaryRole: string | null;
  bio: string | null;
  agents: string[];
  mainAgent: string | null;
  playerCard: PlayerCardView | null;
  /** Highest rank reached; shown beside the current one. */
  peakRank: string | null;
  /** Discord profile banner (Nitro only), behind the stats on the full card. */
  bannerUrl: string | null;
  /** Discord accent colour as 0xRRGGBB: the stats background when there's no banner. */
  accentColor: number | null;
  /** ISO timestamp of the last profile save (or creation). */
  updatedAt: string;
  /** Admin-assigned handicap; null for none. See lib/nerfs. */
  nerfTier: NerfTier | null;
}

export type NerfTier = "TIER_1" | "TIER_2";

/** Suggested profile values from a Riot ID's recent games */
export interface ProfileImport {
  riotId: string | null;
  currentRank: string | null;
  peakRank: string | null;
  playerCardId: number | null;
  mainAgent: string | null;
  agents: string[];
  primaryRole: string | null;
  secondaryRole: string | null;
  matchesAnalyzed: number;
}

export interface TournamentView {
  id: number;
  name: string;
  slug: string;
  status: TournamentStatus;
  creditBudget: number;
  rosterSize: number;
  minBid: number;
  /** ISO start time; null until an admin announces a date. */
  startsAt: string | null;
}

export interface RegistrationView {
  id: number;
  tournamentId: number;
  status: RegistrationStatus;
  player: ProfileView;
}

export interface TeamMemberView {
  id: number;
  profileId: number;
  username: string;
  pricePaid: number;
}

export interface TeamListView {
  id: number;
  tournamentId: number;
  name: string;
  logoUrl: string | null;
  captainUserId: number;
  remainingCredits: number;
  roster: TeamMemberView[];
}

export interface PlayerSummary {
  profileId: number;
  username: string;
  avatarUrl: string | null;
  riotId: string | null;
  currentRank: string | null;
  primaryRole: string | null;
  secondaryRole: string | null;
}

export interface LotView {
  lotId: number;
  seq: number;
  /** The draft pick this is (or will be, while open); null if unsold or still queued. */
  pick: number | null;
  status: LotStatus;
  player: PlayerSummary;
  /** While open: the current price (0 before any bid). Once closed: what it sold for. */
  winningBid: number;
  /** While open: who holds the player (the nominating team until someone bids). */
  winningTeamId: number | null;
  winningTeamName: string | null;
  /** The lowest bid accepted right now: one over the current price. */
  minBid: number;
  endsAt: string | null;
  version: number;
}

export interface RosterEntry {
  profileId: number;
  username: string;
  pricePaid: number;
}

export interface AuctionTeamView {
  teamId: number;
  name: string;
  logoUrl: string | null;
  captainUserId: number;
  captainUsername: string | null;
  remainingCredits: number;
  rosterCount: number;
  rosterSize: number;
  /** Budget ceiling the backend will accept right now. Drives the disabled state. */
  maxBid: number;
  roster: RosterEntry[];
}

export interface BidView {
  bidId: number;
  lotId: number;
  teamId: number;
  teamName: string | null;
  amount: number;
  createdAt: string | null;
}

export interface AuctionSnapshot {
  auctionId: number;
  tournamentId: number;
  status: AuctionStatus;
  currentLot: LotView | null;
  teams: AuctionTeamView[];
  /** Bids on the player up now, newest first; empty between players. */
  recentBids: BidView[];
  pendingLots: number;
  /** The team whose captain nominates next; null outside a running draft. */
  turnTeamId: number | null;
  /** That captain's pick, waiting for the admin to open bidding; null until they choose. */
  pickedPlayer: PlayerSummary | null;
  serverTime: string;
}

export type AuctionMessageType =
  | "LOT_OPENED"
  | "BID_PLACED"
  | "LOT_CLOSED"
  | "STATUS_CHANGED"
  | "SNAPSHOT";

export interface AuctionMessage {
  type: AuctionMessageType;
  auctionId: number;
  status: AuctionStatus | null;
  lot: LotView | null;
  bid: BidView | null;
  teams: AuctionTeamView[] | null;
  note: string | null;
  serverTime: string;
}

export interface BidError {
  lotId: number | null;
  message: string;
  serverTime: string;
}

export interface ApiError {
  error: string;
  message: string;
  timestamp: string;
}
