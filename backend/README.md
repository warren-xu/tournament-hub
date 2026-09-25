# Warrenament — backend

Spring Boot 4.1 backend for a Valorant tournament hub: player profiles, teams, and a live
auction draft where captains bid on nominated players from a fixed credit budget.

## Running it

```bash
# The pom targets Java 25, so point JAVA_HOME at it if your shell default is older.
export JAVA_HOME=$(/usr/libexec/java_home -v 25)

# Postgres starts automatically via compose.yaml (Spring Boot Docker Compose support).
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Flyway creates the schema on first boot. `ddl-auto` is `validate` — the migrations in
`src/main/resources/db/migration` own the schema, Hibernate only checks the mapping agrees.

```bash
./mvnw test          # unit tests + Testcontainers integration tests (needs Docker)
```

## Signing in

**Production: Discord OAuth2.** Create an app at
<https://discord.com/developers/applications>, add redirect URI
`http://localhost:3000/login/oauth2/code/discord` (port 3000, because Next.js proxies
through to this service), scope `identify`. Then:

Put the credentials in `backend/.env` (gitignored) rather than your shell — Spring
loads it via `spring.config.import` in `application.yml`:

```bash
cp .env.example .env    # then fill it in
```

```properties
DISCORD_CLIENT_ID=...
DISCORD_CLIENT_SECRET=...
ADMIN_DISCORD_IDS=your_discord_id   # promotes you to ADMIN on login
FRONTEND_URL=http://localhost:3000
```

It is parsed as a **properties** file, so no `export`, and no quotes around values —
quotes would become part of the secret. Environment variables still work and take
precedence, which is what you want in deployment.

The redirect URI is pinned to `FRONTEND_URL` rather than derived from the incoming
request. The browser reaches the backend through the Next.js proxy, which rewrites the
`Host` header, so a `{baseUrl}` placeholder would resolve to `:8080` and send users
past the frontend — and would not match what Discord has registered.

Admins are configured by Discord id rather than stored as a flag, so the promotion survives
a database reset.

**Dev profile: `POST /api/dev/login?username=X&admin=true`.** Mints a session without
Discord. Driving an auction needs several distinct signed-in captains at once, and one
Discord account per test captain is not workable. The endpoint only exists under the `dev`
profile.

## Frontend wiring

Next.js runs on `:3000` and proxies `/api/*`, `/oauth2/*`, `/login/*` and `/ws` to `:8080`.
That makes everything same-origin, so the session cookie works on both REST calls and the
WebSocket handshake — no CORS config and no token handling in the browser.

```ts
// next.config.ts
async rewrites() {
  return [{ source: '/:path(api|oauth2|login|ws)/:rest*', destination: 'http://localhost:8080/:path/:rest*' }];
}
```

## API

| Method | Path | Who |
|---|---|---|
| `GET` | `/api/me` | anyone (204 when signed out) |
| `GET PUT` | `/api/profiles/me` | signed in |
| `DELETE` | `/api/profiles/{id}` | admin — hard delete, for clearing test players |
| `GET` | `/api/profiles`, `/api/profiles/{id}` | anyone |
| `POST` | `/api/tournaments` | admin |
| `PUT` | `/api/tournaments/{id}/status` | admin |
| `POST` | `/api/tournaments/{id}/registrations` | signed in (self) |
| `PUT` | `/api/tournaments/registrations/{id}?status=` | admin |
| `GET POST` | `/api/tournaments/{id}/teams` | anyone / signed in |
| `PUT DELETE` | `/api/teams/{id}` | that team's captain, or admin |
| `GET` | `/api/agents` | anyone — the picker roster (`?includeRetired=true` for all) |
| `GET` | `/api/ranks` | anyone — competitive tiers (`?includeHidden=true` for all) |
| `GET` | `/api/player-cards` | anyone — saved player-card catalog |
| `POST` | `/api/player-cards/sync` | admin — sync player cards from valorant-api.com |
| `POST` | `/api/agents/sync`, `/api/ranks/sync` | admin — pull from valorant-api.com |
| `POST PUT DELETE` | `/api/agents`, `/api/agents/{id}` | admin — manual fallback |
| `PUT` | `/api/ranks/{id}` | admin — rename or hide a tier |
| `GET` | `/api/auctions/{id}` | anyone — full room snapshot |
| `POST` | `/api/auctions/{id}/{start,pause,resume,nominate,undo,complete}` | admin |

Bidding is the only thing that goes over STOMP:

- subscribe `/topic/auction/{auctionId}` — `LOT_OPENED`, `BID_PLACED`, `LOT_CLOSED`, `STATUS_CHANGED`
- send `/app/auction/{auctionId}/bid` — `{ "lotId": 1, "amount": 25 }`
- rejections arrive on `/user/queue/errors`, visible only to the bidder who was refused

## Deleting a player

`DELETE /api/profiles/{id}` (admins only) permanently removes the profile, agent pool,
registrations and the sign-in account. It exists for clearing test entries before an
event, so it is a real delete rather than a soft one.

It refuses, with a reason, in the cases where deleting would corrupt rather than tidy:

- the player is **on a roster** — deleting would tear them off a team whose credits are
  already spent
- the player **captains a team**
- the player is in an **open or sold lot**
- an admin is deleting **their own account**

Two things survive on purpose, by releasing their reference to the user rather than
cascading into them: `bids` rows keep the amount and team with `user_id` nulled, so a
draft's audit trail stays reconstructable, and a tournament keeps running with
`created_by_user_id` nulled. Queued (`PENDING`/`UNSOLD`) lots have no bids yet and are
removed along with the player.

Note that Discord login re-creates a user on next sign-in, so a delete is not a ban.

## Reference data: agents and ranks

Agents (`agents`, `V2`) and competitive ranks (`ranks`, `V4`) are both synced from
valorant-api.com with one button on `/admin/game-data`. Each table is seeded by its
migration, and `V5` backfills the portraits, rank icons and tier colours, so a fresh
database renders correctly **before** anyone presses Sync and without network access.
The sync then corrects anything stale and adds whatever has been released since.

Ranks are full sub-tiers — Iron 1 through Radiant — keyed on Riot's numeric `tier`,
which is both the stable key and the correct ascending sort order. Two quirks of the
feed that `ValorantApiRankCatalog` handles: it ships **every past episode's** tier table
(only the last is current — Ascendant does not exist in the earlier ones), and the
current set is padded with `Unused1`/`Unused2` placeholders that have no icon.

Reconciliation rules, identical in `AgentSyncService` and `RankSyncService`:

- **Matched on a stable upstream key** — the agent uuid, or the rank's numeric tier —
  falling back to name for agents so migration-seeded rows get adopted rather than
  duplicated. A rename upstream is therefore followed, not treated as a new entry.
- **Nothing is ever deleted.** An agent the source stops listing is reported back in
  `notInSource` and left in place, because `player_profile_agents` stores the agent
  **name**, not a foreign key — deleting would leave it showing on cards that list it.
- **`active` is never overwritten.** The admin page no longer exposes retiring or
  hiding, but the flag and its `PUT` endpoints remain, and a sync deliberately will not
  clear one that was already set.
- **An empty response is treated as a failure**, not as "remove everything".

Override the sources with `app.agents.source-url` and `app.ranks.source-url`.

## How the auction engine holds together

Everything else here is CRUD. These are the parts that cost you a live event if they're wrong.

**Budget ceiling.** A captain with 100 credits and 5 roster slots cannot bid 100 on one
player and leave four slots unfillable, so every still-open slot after this one keeps
`minBid` in reserve:

```
maxBid = remainingCredits - (openSlots - 1) * minBid
```

`TeamView.maxBid` ships this to the client so the UI can disable illegal bids rather than
let a captain slam a button that will be rejected. The server enforces it regardless.

**Concurrency.** Two captains clicking in the same millisecond is the normal case, not the
edge case. `BidService.placeBid` locks the lot row (`PESSIMISTIC_WRITE`) for the whole
transaction, so bids apply one at a time and each validates against the real current price
rather than a stale read. The integration test fires 20 concurrent bids and asserts the
accepted amounts strictly increase — that ordering is what proves the serialisation.

**Broadcast after commit.** Updates go out from an `AFTER_COMMIT` transaction listener, so
viewers never see a bid that then rolls back. Payloads are built inside the transaction and
carried on the event, which also keeps lazy loading out of the listener.

**Closing lots.** A 500ms sweeper closes any `OPEN` lot past its `endsAt`. A polling sweeper
beats per-lot in-memory timers because it survives a restart: kill the backend mid-lot and
the lot still closes correctly on the way back up. `closeLot` is idempotent, so the sweeper
and an admin can both call it without double-charging.

**Anti-snipe.** A bid landing inside `antiSnipeSeconds` of the end pushes the end out, so a
lot can't be won by clicking last.

**Pause.** Freezes the countdown into `lots.paused_remaining_ms` and clears `endsAt`, which
is what takes the lot out of the sweeper's view. Resume restores the remaining time — nobody
loses seconds to an admin timeout.

**Undo.** Reverses the most recent sale: refunds the team, removes the roster entry, returns
the player to the queue. The `bids` table is append-only and is left intact — when a draft
goes wrong mid-event, that table is how you reconstruct it.

## Known gaps

- **CSRF is disabled.** There's no cookie-authenticated form post that isn't JSON from our
  own origin, but turn it back on (`CookieCsrfTokenRepository` + an `X-XSRF-TOKEN` header
  from the client) before exposing this publicly.
- **Single instance only.** The STOMP broker is in-memory, so one backend node owns one
  auction. Running more than one node needs a broker relay and shared auction state.
- **No rate limiting** on bids beyond the domain rules.

### Player card artwork

`V8` adds the `player_cards` catalog and an optional `player_card_id` on profiles.
Use **Sync player cards** on `/admin/game-data` (or the combined sync button) to
populate and refresh it from `https://valorant-api.com/v1/playercards`.
`GET /api/player-cards` reads only the database: neither startup nor profile/page
loads call the external catalog. Images are served from the asset CDN through
Next.js image optimization. Override the sync source with `app.player-cards.source-url`.

Sync matches cards by their upstream UUID, updates names and artwork, and retains
cards missing upstream so saved choices remain valid. It returns the same
added/updated/unchanged/notInSource summary as agents and ranks.

Pass `playerCardId` in `PUT /api/profiles/me` to select a synced card, or `null` to
return to main-agent artwork. Unknown IDs are rejected. Profile responses include
the selected `playerCard` object (ID, name, small/portrait artwork), or `null`.
The profile picker searches the saved catalog and renders 24 thumbnails per page.

`V10` makes player cards portrait-only: it removes entries without `largeArt`,
clears affected profile selections (falling back to agent artwork), and drops the
unused wide-art column. Future syncs skip entries without portrait artwork. Existing
portrait cards keep their IDs and selections, even if a later source omits artwork.
