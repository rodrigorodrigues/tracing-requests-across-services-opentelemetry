create table if not exists user_auth
(
    id           serial primary key,
    username     varchar(255) not null ,
    active       boolean      not null default true,
    balance      numeric(38, 2) not null,
    full_name    varchar(255) not null,
    address      varchar(255) not null,
    password     varchar(255),
    social_media boolean      not null default false,
    CONSTRAINT idx_username UNIQUE(username)
);

create table if not exists payment
(
    id                                serial primary key,
    request_id                        varchar(255) not null,
    auth_check_processed              boolean      not null default false,
    created_at                        timestamp(6) with time zone,
    processed_at                      timestamp(6) with time zone,
    message                           varchar(255),
    sanction_check_processed          boolean      not null default false,
    status                            varchar(255) not null,
    total                             numeric(38, 2),
    user_confirmation_check_processed boolean      not null default false,
    username_from                     varchar(255) not null,
    username_to                       varchar(255) not null,
    CONSTRAINT idx_request_id UNIQUE(request_id),
    CONSTRAINT fk_username_from FOREIGN KEY (username_from) REFERENCES user_auth (username),
    CONSTRAINT fk_username_to FOREIGN KEY (username_to) REFERENCES user_auth (username)
);