-- The roster is synced from valorant-api.com rather than maintained by hand.
-- external_id is Riot's agent uuid: a stable key that survives a rename, which the
-- display name does not.
alter table agents add column external_id varchar(64);
alter table agents add column icon_url varchar(512);

create unique index idx_agents_external_id on agents (external_id)
    where external_id is not null;
