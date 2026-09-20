package org.cassandraunit.test.cql;

import com.datastax.oss.driver.api.core.cql.Row;
import org.cassandraunit.CassandraCQLUnit;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.junit.Rule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code keyspaceCreation = false} when the script has its own {@code CREATE KEYSPACE}; queries
 * then have to name that keyspace.
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
