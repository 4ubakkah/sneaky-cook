-- Recipe schema [REQ-12]. Instruction full-text search is index-backed
-- [REQ-9]: the tsvector column is generated from the instructions and stays
-- fresh on every UPDATE by construction.

CREATE TABLE recipe (
    id               uuid PRIMARY KEY,
    name             varchar(200) NOT NULL,
    vegetarian       boolean      NOT NULL,
    servings         int          NOT NULL CHECK (servings > 0),
    instructions     text         NOT NULL,
    created_at       timestamptz  NOT NULL,
    instructions_tsv tsvector GENERATED ALWAYS AS (to_tsvector('english', instructions)) STORED
);

CREATE INDEX idx_recipe_instructions_fts ON recipe USING gin (instructions_tsv);

-- Ingredients are plain strings owned by the recipe, stored lower-cased so
-- include/exclude filters are plain equality [REQ-7, REQ-8]. Position keeps
-- the order the client sent.
CREATE TABLE recipe_ingredient (
    recipe_id uuid         NOT NULL REFERENCES recipe (id) ON DELETE CASCADE,
    position  int          NOT NULL,
    name      varchar(100) NOT NULL,
    PRIMARY KEY (recipe_id, position)
);

-- Serves the EXISTS / NOT EXISTS subqueries of the ingredient filters.
CREATE INDEX idx_recipe_ingredient_name ON recipe_ingredient (recipe_id, name);
