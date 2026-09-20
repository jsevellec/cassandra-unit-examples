package org.cassandraunit.test.junit6;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import org.cassandraunit.CQLDataLoader;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loading schema and data from separate scripts. The first load owns the keyspace
 * ({@code true, true} drops then creates it, and issues the {@code USE}); the second passes
 * {@code false, false} and runs against it.
 */
class MultipleDataSetsTest {

    private static final String KEYSPACE = "multipledatasets";

    private static CqlSession session;

    @BeforeAll
    static void loadSchemaThenData() throws Exception {
        EmbeddedCassandraServerHelper.startEmbeddedCassandra();
        session = EmbeddedCassandraServerHelper.getSession();

        CQLDataLoader loader = new CQLDataLoader(session);
        loader.load(new ClassPathCQLDataSet("schema.cql", true, true, KEYSPACE));
        loader.load(new ClassPathCQLDataSet("data.cql", false, false, KEYSPACE));
    }

    @Test
    void the_schema_script_created_the_table() {
        Row row = session.execute(
                "select table_name from system_schema.tables"
                        + " where keyspace_name = '" + KEYSPACE + "' and table_name = 'widget'").one();

        assertThat(row).isNotNull();
    }

    @Test
    void the_data_script_populated_it() {
        Row row = session.execute("select label from " + KEYSPACE + ".widget where id = 1").one();

        assertThat(row).isNotNull();
        assertThat(row.getString("label")).isEqualTo("hello");
    }
}
