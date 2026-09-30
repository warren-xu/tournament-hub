-- The order teams take turns nominating in (1 goes first) and are listed in. Admins can
-- change it before the draft starts. Existing teams keep their creation order.
alter table teams add column draft_order int;
update teams t
set draft_order = o.n
from (select id, row_number() over (partition by tournament_id order by id) as n from teams) o
where o.id = t.id;
alter table teams alter column draft_order set not null;
alter table teams alter column draft_order set default 0;
