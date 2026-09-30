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
HENRIK_API_KEY=...                 # optional: "fill in from a match" on /profile
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

Next.js proxies `/api/*`, `/oauth2/*` and `/login/*` to the backend (`BACKEND_URL`), so REST
calls are same-origin and the session cookie rides along with no CORS config.

The auction WebSocket (`/ws`) cannot go through that proxy, so the browser connects to the
backend directly (`NEXT_PUBLIC_WS_URL`). Deployed, the backend is a different site from the
frontend and the cookie never reaches it, so a bidder first calls `POST /api/ws-ticket`
(through the proxy, cookie included) and sends the single-use, 60-second ticket in the STOMP
`CONNECT` frame. Without a ticket the socket can still watch, but cannot bid.

## Deploying

Everything runs on free tiers:

| Piece | Host |
|---|---|
| Frontend | Vercel (`frontend/`, default `*.vercel.app` domain) |
| Backend | A 1 GB Oracle Cloud VM, run by systemd behind Caddy |
| HTTPS for the backend | Caddy with a Let's Encrypt certificate for `<vm-ip-with-dashes>.sslip.io` |
| Postgres | Neon (direct connection, not the pooled one) |

### Configuration

Secrets live only in `/etc/warrenament.env` on the VM (owned by root, mode `600`), never in
the repo.

To add or change a value:

```bash
sudo nano /etc/warrenament.env        # one KEY=value per line, no quotes, no spaces around =
sudo systemctl restart warrenament
```

### Releasing a new backend

Build on your own machine (Maven needs more memory than the VM has), copy the jar over, and
restart. Flyway applies any new migrations on startup.

Startup takes a minute or two on the VM's fraction of a CPU. To check on it:

```bash
journalctl -u warrenament -f                 # follow the log
curl -s localhost:8080/actuator/health       # on the VM: {"status":"UP"}
```

The frontend deploys separately with `npx vercel --prod` from `frontend/`.

## API

| Method | Path | Who |
|---|---|---|
| `GET` | `/api/me` | anyone (204 when signed out) |
| `GET PUT` | `/api/profiles/me` | signed in |
| `POST` | `/api/profiles/me/import` | signed in — suggests fields from a Riot ID's recent competitive games via HenrikDev; saves only peak rank, which `PUT /me` never sets |
| `DELETE` | `/api/profiles/{id}` | admin — hard delete, for clearing test players |
| `PUT` | `/api/profiles/{id}/nerf` | admin — `{tier}`: `TIER_1` (Viper or Sage only, Warden max; the auction treats them as Sentinel / Controller), `TIER_2` (Warden max), or `null` |
| `GET` | `/api/profiles`, `/api/profiles/{id}` | anyone |
| `POST` | `/api/tournaments` | admin |
| `PUT` | `/api/tournaments/{id}/status` | admin |
| `PUT` | `/api/tournaments/{id}/schedule` | admin — `{startsAt}` (ISO, or `null` for TBA) |
| `POST` | `/api/tournaments/{id}/registrations` | signed in (self) — approved on the spot and queued |
| `DELETE` | `/api/tournaments/{id}/registrations/me` | signed in (self) — leave the queue, before the draft starts |
| `POST` | `/api/tournaments/{id}/queue` | admin — add `{playerProfileIds}` to the queue |
| `DELETE` | `/api/tournaments/{id}/queue/{profileId}` | admin — take a player back out of the queue |
| `GET POST` | `/api/tournaments/{id}/teams` | anyone / signed in |
| `PUT` | `/api/tournaments/{id}/teams/order` | admin — `{teamIds}` first to last: the order teams nominate in and are listed in; before the draft starts |
| `PUT` | `/api/teams/{id}` | that team's captain, or admin — rename |
| `DELETE` | `/api/teams/{id}` | admin — before the draft starts; the captain's seat goes with it |
| `GET` | `/api/agents` | anyone — the picker roster (`?includeRetired=true` for all) |
| `GET` | `/api/ranks` | anyone — competitive tiers (`?includeHidden=true` for all) |
| `GET` | `/api/player-cards` | anyone — saved player-card catalog |
| `POST` | `/api/player-cards/sync` | admin — sync player cards from valorant-api.com |
| `POST` | `/api/agents/sync`, `/api/ranks/sync` | admin — pull from valorant-api.com |
| `POST PUT DELETE` | `/api/agents`, `/api/agents/{id}` | admin — manual fallback |
| `PUT` | `/api/ranks/{id}` | admin — rename or hide a tier |
| `GET` | `/api/auctions/{id}` | anyone — full room snapshot |
| `POST` | `/api/auctions/{id}/{start,pause,resume,nominate,complete}` | admin |

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

**Live bidding.** When a lot opens, the team whose captain nominated holds the player at
0. Any other captain can take the lead with a bid at least one credit over the current price
(`minBid`, 1 by default, is the first step), up to everything their team has left; nothing
is held back for later slots. The team in the lead can't raise itself. Each bid restarts a
10-second countdown (`BidService.BID_RESET_SECONDS`) without ever shortening the clock.
Whoever holds the player when time runs out takes them at that price; if nobody bid, the
nominator keeps them for 0. `TeamView.maxBid` ships the ceiling so the UI can disable
illegal bids; the server enforces it regardless.

A lot closes early the moment no other team could outbid (no free slot, or not enough
credits for the next step, per `BudgetRules.canBid`). Late in a draft, when everyone else is
broke or full, each nomination settles immediately instead of running a dead clock.

**Concurrency.** Two captains clicking in the same millisecond is the normal case, not the
edge case. `BidService.submitBid` locks the lot row (`PESSIMISTIC_WRITE`) for the whole
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

**No undo.** Sales are final. The `bids` table is append-only — when a draft goes wrong
mid-event, that table is how you reconstruct it.

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

### Draft pool, queue and start times

The draft pool is everyone in the auction queue: a player joins it by signing up
(`POST .../registrations`, needs a current rank), or an admin adds chosen players by hand
(`POST .../queue`). The tournament page shows players the sign-ups
and shows admins every profile, so they can pick who to add. An `APPROVED` registration marks a
player as queued and always has a matching lot; removing either removes both. The queue can
change any number of times until the draft starts, then it's locked along with the teams.

The queue always runs highest rank first (by the ranks catalog's tier; no rank sorts last),
then by who joined first. It's sorted whenever it's read rather than stored in order, so a
queued player who edits their rank moves with it, and "nominate next" follows the same order
(players who drew no bids go after everyone not yet nominated).

`POST /api/tournaments` takes the captains (at least two) and nothing else about players:
it creates a team per captain with them seated (price 0), opens sign-ups (`REGISTRATION`)
and creates the auction with an empty queue. Adding a team later from the tournament page
seats its captain the same way and takes them out of the queue.

Team size is set when the draft first starts: captains plus queued players, divided across
the teams and rounded up. If that doesn't divide evenly some teams finish a player short;
nobody is turned away, since a team can carry a substitute. Start is refused only when
nobody is queued. Bids stop once a team reaches that size.

`V17` adds a nullable `starts_at` to tournaments (null means "to be announced"). The
frontend serves it as add-to-calendar links: a Google Calendar template link and an .ics
file at `/t/{slug}/calendar.ics`, both covering three hours from the start.
