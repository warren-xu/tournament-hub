-- Bids can start at 0: a captain may take a player nobody else wants for free. Not bidding
-- is still how a captain passes. Tournaments whose draft hasn't started move to the new floor.
alter table tournaments alter column min_bid set default 0;
update tournaments set min_bid = 0 where status in ('DRAFT', 'REGISTRATION');
