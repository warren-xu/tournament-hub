-- Starting an auction used to leave its tournament at DRAFT or REGISTRATION, so a draft
-- in progress showed as "Setting up". The code now moves the tournament to DRAFTING on
-- start; this corrects tournaments whose auction was started before that fix.
update tournaments
set status = 'DRAFTING'
where status in ('DRAFT', 'REGISTRATION')
  and id in (select tournament_id from auctions where status in ('LIVE', 'PAUSED'));
