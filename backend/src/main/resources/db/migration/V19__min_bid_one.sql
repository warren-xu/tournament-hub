-- Back to a floor of 1 credit (undoing V18): every winning bid costs something. Not bidding
-- is how a captain passes. Tournaments whose draft hasn't started move back to the floor.
alter table tournaments alter column min_bid set default 1;
update tournaments set min_bid = 1 where status in ('DRAFT', 'REGISTRATION');
