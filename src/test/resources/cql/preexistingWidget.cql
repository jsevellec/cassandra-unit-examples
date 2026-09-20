-- One row that exists before any row dataset loads, so "column absent" (leave the value alone)
-- reads differently from "column present with null" (write a tombstone).

INSERT INTO widget (id, label) VALUES (00000000-0000-0000-0000-000000000009, 'preexisting');
