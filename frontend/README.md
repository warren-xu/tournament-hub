# Warrenament — frontend

Next.js 16 (App Router, Turbopack) front end for the Valorant tournament hub.
Read-only pages are Server Components; the auction room is a client component driven
by STOMP over WebSocket.

## Running it

The backend must be up first — see `../backend/README.md`.

```bash
npm run dev     # http://localhost:3000
```

`.env.local` holds the three settings that matter:

| Variable | Purpose |
|---|---|
| `BACKEND_URL` | Where Server Components fetch from, and what `next.config.ts` proxies to |
| `NEXT_PUBLIC_WS_URL` | The STOMP endpoint the browser dials |
| `NEXT_PUBLIC_DEV_AUTH` | Shows the local sign-in panel on `/signin` |

## Routes

| Route | What it is |
|---|---|
| `/` | Tournament list |
| `/players` | The player pool |
| `/profile` | Your player card (the thing captains read while bidding on you) |
| `/signin` | Discord OAuth, plus a local shortcut in development |
| `/admin/game-data` | Agent roster and rank tiers, synced (admins only) |
| `/t/[slug]` | Tournament: rosters, draft pool, admin setup |
| `/t/[slug]/draft` | The live auction room |

## How it talks to the backend

**REST goes through a proxy.** `next.config.ts` rewrites `/api/*`, `/oauth2/*` and
`/login/*` to Spring. Everything is same-origin, so the session cookie just works — no
CORS config, no tokens in the browser. Server Components skip the proxy and call the
backend directly, forwarding the caller's cookie via `cookies()`.

**The WebSocket does not.** Next's rewrites do not proxy WebSocket upgrades (verified,
not assumed), so the browser dials `NEXT_PUBLIC_WS_URL` straight at the backend.
That still authenticates, because cookies are scoped by host and ignore port: the
`JSESSIONID` set through the proxy on `:3000` is sent on a handshake to `:8080`.

## The auction room

`lib/use-auction.ts` owns the connection and mirrors auction state locally. Two things
it has to get right:

- **Out-of-order messages.** Every lot update carries the monotonic `version` the
  backend stamps on it. Anything older than what is already rendered is dropped.
- **Missed messages.** After any reconnect the local mirror is thrown away and replaced
  with a fresh `GET /api/auctions/{id}` snapshot, rather than trying to replay history.

The countdown runs off the server's `endsAt` and a measured clock offset, never off a
local duration, so every client agrees and an anti-snipe extension appears immediately.

Bid buttons are disabled against `TeamView.maxBid`, the same ceiling the server
enforces, and the blocking reason is always stated in words underneath rather than left
as a dead button. The server re-checks everything regardless.

## Reference data

**Nothing about the game is hardcoded any more.** `lib/valorant.ts` holds only grouping
helpers and a fallback role list.

Agents come from `GET /api/agents` and ranks from `GET /api/ranks`; an admin pulls both
from valorant-api.com with one button on `/admin/game-data`. Ranks are full sub-tiers
(Iron 1 → Radiant), and the sync brings agent portraits plus per-tier rank icons and
Riot's own tier colours.

Those icons are used where they help a captain read a player quickly: the rank badge
appears on the player pool cards and on whoever is currently up for auction. Role
options on the profile form are derived from the agent table too, so a new role
category needs no frontend edit either.

Profiles store rank and agents **by name**, not by id, so reference data can change
without breaking existing cards — the icon is resolved at render time via `rankIndex()`.

## Design

One dominant neutral (cold near-black through bone) and one accent (Valorant red),
rationed to live state, the current bid, the primary action and the active nav item.
Hard 1px borders, no radius, no shadows, no gradients. Montserrat for headings,
Inter for text. The single angular flourish is `.corner-cut`, on the lot card and
primary buttons only.

Numbers that change in place — budgets, bids, countdowns — use `.tabular` so they do
not reflow as they tick.
