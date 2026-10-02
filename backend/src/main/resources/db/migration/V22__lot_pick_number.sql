-- The order players were actually drafted in: 1 for the first player sold in an auction,
-- 2 for the next. Null until a lot sells. Lots already sold are numbered by when their
-- player joined a team.
alter table lots add column pick_number int;
update lots l
set pick_number = o.n
from (select l2.id,
             row_number() over (partition by l2.auction_id order by tm.acquired_at, tm.id) as n
      from lots l2
      join auctions a on a.id = l2.auction_id
      join team_members tm on tm.tournament_id = a.tournament_id
                          and tm.player_profile_id = l2.player_profile_id
      where l2.status = 'SOLD') o
where o.id = l.id;
