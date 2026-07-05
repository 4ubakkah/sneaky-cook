-- Users and per-user recipe ownership [REQ-17, REQ-18] (spec §13).
-- Pre-auth recipe rows have no owner to backfill from — they are fixture data
-- only (spec caveat 6) and are wiped before ownership becomes mandatory.

DELETE FROM recipe;

CREATE TABLE app_user (
    id            uuid         PRIMARY KEY,
    username      varchar(50)  NOT NULL UNIQUE,
    password_hash varchar(100) NOT NULL,
    created_at    timestamptz  NOT NULL
);

ALTER TABLE recipe
    ADD COLUMN owner_id uuid NOT NULL REFERENCES app_user (id);

-- The new workhorse index: every recipe query now starts with the ownership
-- predicate (spec §13).
CREATE INDEX idx_recipe_owner ON recipe (owner_id);
