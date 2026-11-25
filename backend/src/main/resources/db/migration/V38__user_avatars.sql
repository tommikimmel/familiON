-- Profile photo per user; kept out of `users` so profile reads never load the bytes.
CREATE TABLE user_avatars (
    user_id         UUID         PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    content_type    VARCHAR(20)  NOT NULL
                        CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    content         BYTEA        NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL
);
