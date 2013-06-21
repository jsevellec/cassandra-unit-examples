CREATE KEYSPACE mykeyspace WITH replication={'class' : 'SimpleStrategy', 'replication_factor':1};
USE mykeyspace;

CREATE TABLE myTable(
    id varchar,
    value varchar,
    PRIMARY KEY(id));

INSERT INTO myTable(id, value) values('myKey01','myValue01');

INSERT INTO myTable(id, value) values('myKey02','myValue02');
