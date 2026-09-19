package org.cassandraunit.test.cql;

import com.datastax.oss.driver.api.core.cql.Row;
import org.cassandraunit.CassandraCQLUnit;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.junit.Rule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * When the CQL script contains its own {@code CREATE KEYSPACE} (and a {@code USE}), pass
 * {@code keyspaceCreation = false} so cassandra-unit does not try to create one for you.
 *
 * <p>Queries then have to name the keyspace the script created.
 */
public class CQLScriptLoadWithoutKeyspaceCreationTest {

    @Rule
    public CassandraCQLUnit cassandraCQLUnit =
            new CassandraCQLUnit(new ClassPathCQLDataSet("simpleWithCreateKeyspace.cql", false));

    @Test
    public void should_have_started_and_execute_cql_script() {
        Row row = cassandraCQLUnit.session
                .execute("select value from mykeyspace.mytable where id = 'myKey01'").one();

        assertThat(row).isNotNull();
        assertThat(row.getString("value")).isEqualTo("myValue01");
    }
}
