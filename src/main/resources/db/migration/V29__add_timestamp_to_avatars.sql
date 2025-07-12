alter table user_avatars
    add column updated_at timestamp default now() not null;