-- One row that exists before any row dataset loads.
--
-- Kept out of widgetSchema.cql so the examples that assert on a table's exact contents can load the
-- schema without it. It is here to make "column absent from the fixture" (leave the existing value
-- alone) visibly different from "column present with null" (write a tombstone over it) - on a table
-- that was empty, both look the same on read-back.

INSERT INTO widget (id, label) VALUES (00000000-0000-0000-0000-000000000009, 'preexisting');
