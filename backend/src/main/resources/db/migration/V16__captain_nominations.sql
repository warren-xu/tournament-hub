-- Nominations rotate through the teams: on its turn, a team's captain picks who goes up
-- next and the admin opens bidding on that pick.
--   turn_team_id: whose turn it is to nominate (null before the start and after the end)
--   pick_lot_id:  the lot that captain has picked, waiting for the admin to open it
alter table auctions add column turn_team_id bigint references teams (id) on delete set null;
alter table auctions add column pick_lot_id bigint references lots (id) on delete set null;

-- Drafts already running get their first turn: the first team (by creation order) with
-- a roster slot still open. Later turns follow in the application as each lot closes.
update auctions a
set turn_team_id = (
    select t.id
    from teams t
    join tournaments tr on tr.id = t.tournament_id
    where t.tournament_id = a.tournament_id
      and (select count(*) from team_members m where m.team_id = t.id) < tr.roster_size
    order by t.id
    limit 1)
where a.status in ('LIVE', 'PAUSED');
