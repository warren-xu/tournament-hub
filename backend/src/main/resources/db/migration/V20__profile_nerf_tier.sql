-- An admin-assigned handicap for strong players, shown in the draft room.
--   TIER_1: Viper or Sage only, and no gun above a Warden (no Phantom or Vandal)
--   TIER_2: the gun limit only
-- Null means no nerf.
alter table player_profiles add column nerf_tier varchar(16);
alter table player_profiles add constraint player_profiles_nerf_tier_check
    check (nerf_tier in ('TIER_1', 'TIER_2'));
