package org.cassandraunit.test.junit5;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import org.cassandraunit.CassandraUnitExtension;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * When the CQL script creates its own keyspace, tell cassandra-unit to keep its hands off by
 * passing {@code keyspaceCreation = false}.
 *
 * <p>The dataset then has no keyspace name of its own, so the loader issues no {@code USE} and
 * queries have to name the keyspace the script created.
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
