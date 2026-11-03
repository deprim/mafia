-- Description: Create initial database schema
-- Tables: game, player, game_phase, game_result, vote, elimination

create table game (
    id uuid primary key default gen_random_uuid(),
    join_code varchar(8) not null unique,
    mafia_count int not null,
    status varchar(20) not null default 'WAITING',
    created_at timestamptz not null default current_timestamp,

    constraint check_game_mafia_count check ( mafia_count > 0 ),
    constraint check_game_status check ( status in ('WAITING', 'STARTED', 'FINISHED') ),
    constraint check_game_join_code_length check ( length(join_code) = 8 )
                  );

create table player (
    id uuid primary key default gen_random_uuid(),
    game_id uuid not null references game(id),
    nickname varchar(40) not null,
    is_host boolean not null default false,
    role varchar(10),
    status varchar(15) not null default 'ALIVE',
    session_token_hash varchar(64) not null unique,
    joined_at timestamptz not null default current_timestamp,

    constraint unique_nickname_per_game unique (game_id, nickname),
    constraint check_player_status check ( status in ('ALIVE', 'DEAD') ),
    constraint check_player_role check ( role in ('MAFIA', 'CIVILIAN') ),
    constraint check_player_nickname_length check ( length(nickname) > 0 )
);

create unique index unique_host_per_game
on player (game_id)
where is_host = true;

create table game_phase (
    id uuid primary key default gen_random_uuid(),
    game_id uuid not null references game(id),
    round_number integer not null,
    phase_type varchar(20) not null,
    status varchar(15) not null default 'OPENED',
    started_at timestamptz not null default current_timestamp,
    ended_at timestamptz,

    constraint check_phase_round check (round_number > 0),
    constraint check_phase_type check (phase_type in ('NIGHT', 'DAY')),
    constraint check_phase_status check (status in ('OPENED', 'CLOSED')),
    constraint check_phase_time check (ended_at is null or ended_at >= started_at),
    constraint unique_game_round_phase unique (game_id, round_number, phase_type),
    constraint check_phase_completion check (
        (status = 'OPENED' and ended_at is null)
            or
        (status = 'CLOSED' and ended_at is not null)
        )
);

create unique index unique_open_phase_per_game
on game_phase (game_id)
where status = 'OPENED';

create table game_result(
    id uuid primary key default gen_random_uuid(),
    game_id uuid not null unique references game(id),
    winning_team varchar(20) not null,
    finished_at timestamptz not null default current_timestamp,

    constraint check_winning_team check (winning_team in ('MAFIA', 'CIVILIAN'))
);

create table vote(
    id uuid primary key default gen_random_uuid(),
    phase_id uuid not null references game_phase(id),
    voter_id uuid not null references player(id),
    target_id uuid not null references player(id),
    created_at timestamptz not null default current_timestamp,

    constraint unique_phase_voter unique (phase_id, voter_id),
    constraint check_vote_not_self check (voter_id <> target_id)
);

create table elimination(
    id uuid primary key default gen_random_uuid(),
    phase_id uuid not null references game_phase(id),
    player_id uuid not null references player(id),
    eliminated_at timestamptz not null default current_timestamp,

    constraint unique_elimination_phase unique (phase_id),
    constraint unique_elimination_player unique (player_id)
);



