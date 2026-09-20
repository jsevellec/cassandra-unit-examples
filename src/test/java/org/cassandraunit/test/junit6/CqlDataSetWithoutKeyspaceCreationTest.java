package org.cassandraunit.test.junit6;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import org.cassandraunit.CassandraUnitExtension;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code keyspaceCreation = false} when the CQL script creates its own keyspace. The loader then
 * issues no {@code USE}, so queries have to name that keyspace.
 */
class CqlDataSetWithoutKeyspaceCreationTest {

    @RegisterExtension
    static CassandraUnitExtension cassandra =
            new CassandraUnitExtension(new ClassPathCQLDataSet("simpleWithCreateKeyspace.cql", false));

    @Test
    void reads_from_the_keyspace_the_script_created(CqlSession session) {
        Row row = session.execute("select value from mykeyspace.mytable where id = 'myKey01'").one();

        assertThat(row).isNotNull();
        assertThat(row.getString("value")).isEqualTo("myValue01");
    }
}
