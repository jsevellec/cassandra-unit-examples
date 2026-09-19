-- Schema for the expected-dataset examples.
--
-- Separate from widgetSchema.cql because an expectation is strict by default: a table it names must
-- hold exactly the rows it lists, so a schema that seeds rows of its own would have to be described
-- by every expectation. It also adds a table with a clustering column, since row order is only a
-- thing that exists - and only assertable - inside one partition.

CREATE TABLE widget (
    id       uuid PRIMARY KEY,
    label    text,
    quantity bigint,
    created  timestamp
);

CREATE TABLE event (
    day  text,
    at   timestamp,
    kind text,
    PRIMARY KEY (day, at)
) WITH CLUSTERING ORDER BY (at DESC);
