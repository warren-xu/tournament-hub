-- Sealed single-round bidding.
--
-- Every captain submits one hidden amount for the player on the block, the highest wins,
-- and a tie is settled by a coin flip. Nobody can see a price to beat, so the two rules
-- that existed to govern out-bidding have nothing left to do: there is no increment to
-- step by, and no last-second bid to extend the clock against.
alter table auctions drop column anti_snipe_seconds;
alter table tournaments drop column min_increment;

-- These columns now hold the result of the reveal rather than a running high bid.
alter table lots rename column current_bid to winning_bid;
alter table lots rename column current_bidder_team_id to winning_team_id;

-- When a lot is opened the clock starts here. A player returned to the queue by an undo
-- and put up again is a fresh round, and the reveal must not count the first round's bids.
alter table lots add column opened_at timestamptz;

-- A captain may resubmit until the window closes and the reveal reads their last bid,
-- so every close walks the bids of one lot, newest first.
create index bids_lot_id_id_desc_idx on bids (lot_id, id desc);

-- Players left over once every captain is out of credits are dealt out at no cost.
comment on column team_members.price_paid is 'Credits paid; 0 for a randomly assigned player';
