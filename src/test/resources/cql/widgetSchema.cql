-- Schema only: no CREATE KEYSPACE and no USE. A row dataset cannot create a schema, which is why
-- every row example loads this first.

CREATE TABLE widget (
    id       uuid PRIMARY KEY,
    label    text,
    tags     set<text>,
    created  timestamp,
    payload  blob,
    quantity bigint,
    props    map<text, int>
);
