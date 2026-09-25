-- Full-body agent art, used as the overlay on player cards. Previously derived in the
-- frontend by string-replacing the icon URL, which broke for any agent whose assets did
-- not follow that exact naming.
alter table agents add column portrait_url varchar(512);

-- Backfill: verified across the whole roster that the portrait sits beside the icon
-- under the same agent uuid. The sync overwrites this with the authoritative value.
update agents
set portrait_url = 'https://media.valorant-api.com/agents/' || external_id || '/fullportrait.png'
where external_id is not null;
