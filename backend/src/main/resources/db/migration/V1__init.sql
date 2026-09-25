-- Users come from Discord OAuth2; discord_id is the stable external key.
create table users (
    id          bigserial primary key,
    discord_id  varchar(64)  not null unique,
    username    varchar(128) not null,
    avatar_url  varchar(512),
    role        varchar(16)  not null default 'PLAYER',
    created_at  timestamptz  not null default now()
);

create table player_profiles (
    id              bigserial primary key,
    user_id         bigint       not null unique references users (id) on delete cascade,
    riot_id         varchar(64),
    current_rank    varchar(32),
    peak_rank       varchar(32),
    primary_role    varchar(32),
    secondary_role  varchar(32),
    bio             varchar(1000),
    updated_at      timestamptz  not null default now()
);

create table player_profile_agents (
    player_profile_id bigint      not null references player_profiles (id) on delete cascade,
    agent             varchar(32) not null,
    primary key (player_profile_id, agent)
);

create table tournaments (
    id                 bigserial primary key,
    name               varchar(128) not null,
    slug               varchar(128) not null unique,
    status             varchar(16)  not null default 'DRAFT',
    credit_budget      int          not null default 100,
    roster_size        int          not null default 5,
    min_bid            int          not null default 1,
    min_increment      int          not null default 1,
    created_by_user_id bigint references users (id),
    created_at         timestamptz  not null default now()
);

-- The draftable player pool for a tournament.
create table registrations (
    id                bigserial primary key,
    tournament_id     bigint      not null references tournaments (id) on delete cascade,
    player_profile_id bigint      not null references player_profiles (id) on delete cascade,
    status            varchar(16) not null default 'PENDING',
    created_at        timestamptz not null default now(),
    unique (tournament_id, player_profile_id)
);

create table teams (
    id                bigserial primary key,
    tournament_id     bigint       not null references tournaments (id) on delete cascade,
    name              varchar(128) not null,
    logo_url          varchar(512),
    captain_user_id   bigint       not null references users (id),
    remaining_credits int          not null,
    created_at        timestamptz  not null default now(),
    unique (tournament_id, name),
    unique (tournament_id, captain_user_id)
);

-- tournament_id is denormalised so the DB itself can enforce
-- "a player is on at most one roster per tournament".
create table team_members (
    id                bigserial primary key,
    tournament_id     bigint      not null references tournaments (id) on delete cascade,
    team_id           bigint      not null references teams (id) on delete cascade,
    player_profile_id bigint      not null references player_profiles (id) on delete cascade,
    price_paid        int         not null,
    acquired_at       timestamptz not null default now(),
    unique (tournament_id, player_profile_id)
);

create table auctions (
    id                  bigserial primary key,
    tournament_id       bigint      not null unique references tournaments (id) on delete cascade,
    status              varchar(16) not null default 'SETUP',
    current_lot_id      bigint,
    lot_duration_seconds int        not null default 30,
    anti_snipe_seconds  int         not null default 10,
    created_at          timestamptz not null default now()
);

create table lots (
    id                     bigserial primary key,
    auction_id             bigint      not null references auctions (id) on delete cascade,
    player_profile_id      bigint      not null references player_profiles (id),
    seq                    int         not null,
    status                 varchar(16) not null default 'PENDING',
    current_bid            int         not null default 0,
    current_bidder_team_id bigint references teams (id),
    ends_at                timestamptz,
    -- Set while the auction is paused so the countdown resumes where it stopped
    -- instead of a captain losing seconds to an admin timeout.
    paused_remaining_ms    bigint,
    version                bigint      not null default 0,
    created_at             timestamptz not null default now(),
    unique (auction_id, player_profile_id)
);

-- Drives the sweeper that closes lots whose timer has run out.
create index idx_lots_status_ends_at on lots (status, ends_at);

-- Append-only audit log. This is how a botched draft gets reconstructed.
create table bids (
    id         bigserial primary key,
    lot_id     bigint      not null references lots (id) on delete cascade,
    team_id    bigint      not null references teams (id),
    user_id    bigint references users (id),
    amount     int         not null,
    created_at timestamptz not null default now()
);

create index idx_bids_lot on bids (lot_id, id desc);
