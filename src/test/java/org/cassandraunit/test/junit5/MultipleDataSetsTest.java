package org.cassandraunit.test.junit5;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import org.cassandraunit.CQLDataLoader;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loading schema and data from separate scripts, which is what you want once a schema is
 * shared by several fixtures.
 *
 * <p>The order of the flags matters. The first load owns the keyspace - {@code (true, true)}
 * means "drop it if it is there, then create it" - and finishes by issuing a {@code USE}. The
 * second passes {@code (false, false)} so it neither drops nor recreates the keyspace, and
 * runs against the one the first load left selected on the session.
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
