-- Schema for the expected-dataset examples. Has a clustering column, since row order only
-- exists inside one partition.

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
