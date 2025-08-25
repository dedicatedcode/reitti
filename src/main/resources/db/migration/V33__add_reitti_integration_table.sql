CREATE TABLE reitti_integrations
(
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT        NOT NULL REFERENCES users (id),
    url          VARCHAR(1024) NOT NULL,
    token        VARCHAR(1024) NOT NULL,
    color        VARCHAR(1024) NOT NULL,
    enabled      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP     NOT NULL,
    updated_at   TIMESTAMP     NULL,
    last_used    TIMESTAMP     NULL,
    version      BIGINT        NOT NULL,
    last_message TEXT          NULL,

    CONSTRAINT fk_reitti_integrations_user FOREIGN KEY (user_id) REFERENCES users (id)
);

INSERT INTO api_tokens(token, user_id, name, created_at, version)
SELECT gen_random_uuid(), to_user, 'Reitti Access', now(), 1
FROM connected_users;

INSERT INTO reitti_integrations(user_id, url, token, color, enabled, created_at, updated_at, last_used, version, last_message)
SELECT cs.from_user, 'http://localhost:8080', at.token, cs.color, TRUE,now(), NULL, NULL, 1, NULL FROM api_tokens at LEFT JOIN connected_users cs ON cs.to_user = at.user_id WHERE at.name = 'Reitti Access';

DROP TABLE connected_users;