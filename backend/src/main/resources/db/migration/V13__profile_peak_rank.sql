-- Highest rank a player has reached, shown beside their current one. Filled by the
-- profile's "Fill in" lookup, or picked by hand.
alter table player_profiles add column peak_rank varchar(32);
