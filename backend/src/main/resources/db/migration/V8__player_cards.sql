create table player_cards (
    id bigserial primary key,
    external_id varchar(255) not null unique,
    name varchar(255) not null,
    small_art varchar(512),
    wide_art varchar(512),
    large_art varchar(512)
);

alter table player_profiles add column player_card_id bigint references player_cards(id);
