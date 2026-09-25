-- Remove wide-only cards while retaining portrait cards and their IDs.
-- Profiles using removed artwork fall back to their main agent.
update player_profiles set player_card_id = null
where player_card_id in (
    select id from player_cards where large_art is null or trim(large_art) = ''
);
delete from player_cards where large_art is null or trim(large_art) = '';
alter table player_cards drop column wide_art;
