create table chat_message (
    id bigserial primary key,
    game_id uuid not null references game(id) on delete cascade,
    payload text not null,
    created_at timestamptz not null default current_timestamp
);

create index idx_chat_message_game_id_id
    on chat_message (game_id, id);
