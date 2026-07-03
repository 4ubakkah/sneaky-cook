-- Full-text helpers [REQ-9]: keep the predicate composable inside JPA
-- Specifications via cb.function(...) instead of forking the search path.

CREATE OR REPLACE FUNCTION fts_match(tsv tsvector, query text)
RETURNS boolean
LANGUAGE sql
STABLE
AS $$
    SELECT tsv @@ websearch_to_tsquery('english', query);
$$;

CREATE OR REPLACE FUNCTION fts_rank(tsv tsvector, query text)
RETURNS real
LANGUAGE sql
STABLE
AS $$
    SELECT ts_rank(tsv, websearch_to_tsquery('english', query));
$$;
