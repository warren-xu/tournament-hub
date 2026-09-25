-- Competitive tiers, synced from valorant-api.com alongside agents.
-- `tier` is Riot's own numeric tier from the current episode's tier table: it is both
-- the stable key and the correct sort order (Iron 1 = 3 ... Radiant = 27).
create table ranks (
    id         bigserial primary key,
    tier       int         not null unique,
    name       varchar(32) not null unique,
    division   varchar(32) not null,
    color      varchar(16),
    icon_url   varchar(512),
    active     boolean     not null default true,
    created_at timestamptz not null default now()
);

create index idx_ranks_tier on ranks (tier);

-- Seeded so the profile form works before the first sync and without network access.
-- The sync fills in icons and colours.
insert into ranks (tier, name, division) values
    (0,  'Unranked',    'Unranked'),
    (3,  'Iron 1',      'Iron'),      (4,  'Iron 2',      'Iron'),      (5,  'Iron 3',      'Iron'),
    (6,  'Bronze 1',    'Bronze'),    (7,  'Bronze 2',    'Bronze'),    (8,  'Bronze 3',    'Bronze'),
    (9,  'Silver 1',    'Silver'),    (10, 'Silver 2',    'Silver'),    (11, 'Silver 3',    'Silver'),
    (12, 'Gold 1',      'Gold'),      (13, 'Gold 2',      'Gold'),      (14, 'Gold 3',      'Gold'),
    (15, 'Platinum 1',  'Platinum'),  (16, 'Platinum 2',  'Platinum'),  (17, 'Platinum 3',  'Platinum'),
    (18, 'Diamond 1',   'Diamond'),   (19, 'Diamond 2',   'Diamond'),   (20, 'Diamond 3',   'Diamond'),
    (21, 'Ascendant 1', 'Ascendant'), (22, 'Ascendant 2', 'Ascendant'), (23, 'Ascendant 3', 'Ascendant'),
    (24, 'Immortal 1',  'Immortal'),  (25, 'Immortal 2',  'Immortal'),  (26, 'Immortal 3',  'Immortal'),
    (27, 'Radiant',     'Radiant');
