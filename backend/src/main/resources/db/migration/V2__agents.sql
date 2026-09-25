-- Agents move out of the frontend source and into data, so the roster can be corrected
-- when Riot ships one without a redeploy.
create table agents (
    id            bigserial primary key,
    name          varchar(64) not null unique,
    role          varchar(32) not null,
    -- Retiring rather than deleting keeps profiles that already list the agent intact:
    -- player_profile_agents stores the name, not a foreign key.
    active        boolean     not null default true,
    display_order int         not null default 0,
    created_at    timestamptz not null default now()
);

create index idx_agents_role_order on agents (role, display_order);

insert into agents (name, role, display_order) values
    ('Jett', 'Duelist', 10),
    ('Raze', 'Duelist', 20),
    ('Phoenix', 'Duelist', 30),
    ('Reyna', 'Duelist', 40),
    ('Yoru', 'Duelist', 50),
    ('Neon', 'Duelist', 60),
    ('Iso', 'Duelist', 70),
    ('Waylay', 'Duelist', 80),

    ('Sova', 'Initiator', 10),
    ('Breach', 'Initiator', 20),
    ('Skye', 'Initiator', 30),
    ('KAY/O', 'Initiator', 40),
    ('Fade', 'Initiator', 50),
    ('Gekko', 'Initiator', 60),
    ('Tejo', 'Initiator', 70),

    ('Brimstone', 'Controller', 10),
    ('Viper', 'Controller', 20),
    ('Omen', 'Controller', 30),
    ('Astra', 'Controller', 40),
    ('Harbor', 'Controller', 50),
    ('Clove', 'Controller', 60),

    ('Sage', 'Sentinel', 10),
    ('Cypher', 'Sentinel', 20),
    ('Killjoy', 'Sentinel', 30),
    ('Chamber', 'Sentinel', 40),
    ('Deadlock', 'Sentinel', 50),
    ('Vyse', 'Sentinel', 60);
