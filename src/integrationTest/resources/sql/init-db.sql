create table message_queue_candidates
(
    id bigserial not null constraint message_queue_candidates_pkey primary key,
    message_type varchar(70) not null,
    time_stamp timestamp default now() not null,
    published timestamp,
    message_information jsonb not null
);

create index idx_message_queue_candidates_time_stamp on message_queue_candidates (time_stamp);

