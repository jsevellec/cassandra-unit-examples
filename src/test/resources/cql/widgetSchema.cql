-- Schema only: no CREATE KEYSPACE and no USE.
--
-- By default CassandraUnit drops the keyspace, creates it again and switches to it before running
-- a script, so a script only has to describe tables. A row dataset cannot create a schema at all,
-- which is why every row example loads this file first.

CREATE TABLE widget (
    id       uuid PRIMARY KEY,
    label    text,
    tags     set<text>,
    created  timestamp,
    payload  blob,
    quantity bigint,
    props    map<text, int>
);
