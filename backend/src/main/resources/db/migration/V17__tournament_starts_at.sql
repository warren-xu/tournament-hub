-- When the event starts: shown on the tournament page and used for add-to-calendar links.
-- Null means the date hasn't been announced yet.
alter table tournaments add column starts_at timestamptz;
