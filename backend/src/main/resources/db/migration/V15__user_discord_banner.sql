-- Discord's profile banner (Nitro only) and accent colour (everyone else), captured at
-- login and shown behind the stats on a player's card. Filled on each user's next login.
alter table users add column banner_url varchar(512);
alter table users add column accent_color integer;
