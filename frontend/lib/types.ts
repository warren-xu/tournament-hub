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
}

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
  status: LotStatus;
  player: PlayerSummary;
  /** 0 until the reveal — every amount is sealed while the lot is open. */
  winningBid: number;
  winningTeamId: number | null;
  winningTeamName: string | null;
  minBid: number;
  /** Who has committed. Public; what they committed is not. */
  lockedInTeamIds: number[];
  /** Captains who can still bid, and so are worth waiting for. */
  captainsExpected: number;
  /** Dealt out by the random fill rather than won with credits. */
  randomlyAssigned: boolean;
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
  /** Revealed bids for a closed lot; empty while one is open. */
  recentBids: BidView[];
  /** The viewer's own sealed bid on the open lot. Nobody else's is sent. */
  yourBid: number | null;
  pendingLots: number;
  serverTime: string;
}

export type AuctionMessageType =
  | "LOT_OPENED"
  | "BID_LOCKED"
  | "LOT_CLOSED"
  | "RANDOM_ASSIGNED"
  | "STATUS_CHANGED"
  | "SNAPSHOT";

export interface AuctionMessage {
  type: AuctionMessageType;
  auctionId: number;
  status: AuctionStatus | null;
  lot: LotView | null;
  bid: BidView | null;
  /** Every captain's sealed bid, sent once — with the LOT_CLOSED reveal. */
  reveal: BidView[] | null;
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
